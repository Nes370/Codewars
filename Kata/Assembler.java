package interpreters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;

public class Assembler {
	
	private ArrayList<String> program;
	private int[] registry; 
	private HashMap<String, Integer> data;
	private HashMap<String, String> stringData;
	private LinkedList<Integer> stack;
    private String entry, output;
    //convert flags to one int
    private boolean carryFlag, parityFlag, adjustFlag, zeroFlag, signFlag, trapFlag, interruptFlag, directionFlag, overflowFlag;
	private int compare;
	
	public Assembler() {}
	
	public Assembler(String code) {
		load(code);
	}
	
	/**
	 * Loads the given code into the Assembler.
	 * @param code
	 */
	public void load(String code) {
		program = new ArrayList<String>(Arrays.asList(code.split("\n")));
	}

	/**
	 * Prepares the code for execution. Removes comments and excess whitespace from the source program. 
	 * Defines goto labels. Builds a clean registry for operation. 
	 */
	private void prepare() { // TODO throw compile time error

		data = new HashMap<String, Integer>();
		stringData = new HashMap<String, String>();
		registry = new int[10];
		syncFlags();
		stack = new LinkedList<Integer>();
		entry = output = new String();
		compare = 0;
		
		boolean dataSegment = false;
		
		for(int i = 0; i < program.size(); i++) {
            
            String line = program.get(i);
            
            // Discard comments
            if(line.contains(";"))
                line = line.substring(0, line.indexOf(';'));
            
            // Remove leading and trailing space
            line = line.trim();
            
            // Remove excess space between parameters, excluding String literals
            line.replaceAll("[\\s]+(?=[^']*(?:'[^']*'[^']*)*$)", " ");
            
            // Update section
            if(line.startsWith("section") || line.startsWith("segemnt")) {
            	if(line.endsWith(".data") || line.endsWith(".bss")) {
            		// parse data section
            		dataSegment = true;
            		
            	} else if(line.endsWith(".text")) {
            		// parse text section
               		dataSegment = false;
            	}
            } else if(dataSegment) {
            	// [variable-name]	define-directive	initial-value	[,initial-value]...
            	
            	// TODO use functions to parse expressions
            	// TODO support double quotes, escaped quotes
            	String[] statement = line.split("[, ]+(?=[^']*(?:'[^']*'[^']*)*$)");
            	switch(statement[1] = statement[1].toLowerCase()) {
            		// initialize constant
            		case "db": case "dw": case "dd": case "dq": case "dt": {
            			if(statement[2].startsWith("\'") || statement[2].startsWith("\""))
            				stringData.put(statement[0], statement[2].substring(1, statement[2].length() - 1));
            			else data.put(statement[0], Integer.parseInt(statement[2]));
            		} break;
            		case "equ": {
            			stringData.put(statement[0], line.substring(statement[0].length() + statement[1].length() + 1));
            		} break;
            		case "resb": {
            			data.put(statement[0], 0);
            		} break;
            	}
            } else /* if(text) */ {
            	// [label]	mnemonic	[operands]	[;comment]
            	
            	// Set entry point
            	if(line.startsWith("global"))
            		entry = line.substring(7);
            	
            	// Define labels
                if(line.contains(":")) {
                    String temp = line.split(" ")[0];
                    if(temp.endsWith(":")) {
                        data.put(line.substring(0, line.indexOf(':')), i);
                        line = line.substring(temp.length());
                    }
                }
            }
            
            program.set(i, line);
            
        }
		
	}
	
	/**
	 * Prepares then executes the program. Returns the output of the program if terminated successfully. Else, returns null.
	 * @return output
	 */
	public String run() { // TODO throw runtime error
		
		prepare();
		
        for(int i = 0; i < program.size(); i++) {
            
            // Split statement into parameters
            String[] statement = program.get(i).split("[, ]+(?=[^']*(?:'[^']*'[^']*)*$)");
            
            /* 
             * TODO support memory addresses.
             * [eax] -> address pointer contained in eax.
             * [eax+esi] -> address pointer contained at eax + esi
             * 
             * Some examples of invalid address calculations include:
             * 		mov eax, [ebx-ecx]		; Can only add register values
             * 		mov [eax+esi+edi], ebx	; At most 2 registers in address computation
             */
            
            switch(statement[0]) {
                
                case "mov":  move(statement[1], statement[2]); break;
                case "push": pushStack(statement[1]); break;
                
                case "inc": increment(statement[1]); break;
                case "dec": decrement(statement[1]); break;
                
                case "add": add(statement[1], statement[2]); 		break;
                case "sub":	subtract(statement[1], statement[2]); 	break;
                case "mul": multiply(statement[1], statement[2]);	break;
                case "div": divide(statement[1], statement[2]); 	break;
                case "mod": mod(statement[1], statement[2]); 		break;
                
                case "cmp": compare(statement[1], statement[2]);	 break;
                case "and": and(statement[1], statement[2]); 		 break;
                case "or":  or(statement[1], statement[2]);			 break;
                case "xor": exclusiveOr(statement[1], statement[2]); break;
                
                case "jmp": 				 i = jump(statement[1]); break;
                case "jne": if(compare != 0) i = jump(statement[1]); break;
                case "je":  if(compare == 0) i = jump(statement[1]); break;
                case "jge": if(compare >= 0) i = jump(statement[1]); break;
                case "jg":  if(compare > 0)  i = jump(statement[1]); break;
                case "jle": if(compare <= 0) i = jump(statement[1]); break;
                case "jl":  if(compare < 0)  i = jump(statement[1]); break;
                
                case "call": stack.add(i); i = jump(statement[1]); break;
                case "ret": i = stack.removeLast(); break;
                
                case "msg": print(statement); break;
                case "end": return output;
                
                case "lahf":  loadFlagsIntoAHRegister(); 			   break;
                case "sahf":  storeAHIntoFlags(); 					   break;
                case "popf":  popStackIntoFlags(statement[1]); 		   break;
                case "pushf": pushFlagRegisterOntoStack(statement[1]); break;
                
                case "cmc": complementCarryFlag(); break;
                case "clc": clearCarryFlag(); 	   break;
                case "stc": setCarryFlag();		   break;
                
                case "cli": clearInterruptFlag(); break;
                case "sti": setInterruptFlag();	  break;
                
                case "cld": clearDirectionFlag(); break;
                case "std": setDirectionFlag();	  break;
                
            }
            
        }
        
        return null;
        
	}
	
	/**
	 * The push instruction places its operand onto the top of the hardware supported stack in memory.
	 * Specifically, push first decrements ESP by 4, then places its operand into the contents of the 32-bit location at address [ESP].
	 * ESP (the stack pointer) is decremented by push since the x86 stack grows down - i.e. the stack grows from high addresses to lower addresses.
	 * <br /><br />
	 * Syntax<br />
	 * push <reg32><br />
	 * push <mem><br />
	 * push <con32><br />
	 * <br /><br />
	 * Examples
	 * push eax — push eax on the stack
	 * push [var] — push the 4 bytes at address var onto the stack
	 * @param string
	 */
	private void pushStack(String x) {
		write("ESP", read("ESP") - 4);
		write(read("ESP"), read(x));
	}

	private void write(int destination, int value) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * Reads from the specified register; else reads value of identifier.
	 * @param x
	 * @return
	 */
	private int read(String x) {
		
		switch(x.toUpperCase()) {
		
			// 32-bit data registers
			case "EAX": return registry[0]; // Primary accumulator, I/O and arithmetic
			case "EBX": return registry[1]; // Base register, indexing                
			case "ECX": return registry[2]; // Count register, loop counters          
			case "EDX": return registry[3]; // Data register, I/O and arithmetic      
			// 32-bit index registers
			case "EDI": return registry[4]; // Source Index, string operations
			case "ESI": return registry[5]; // Destination Index, string operations
			// 32-bit pointer registers
			case "ESP": return registry[6]; // Stack pointer
			case "EBP": return registry[7]; // Base pointer
			// 32-bit control registers
			case "EIP":    return registry[8];
			case "EFLAGS": return registry[9];
			
			// 16-bit registers
			case "AX": return registry[0] % 0x1_0000;
			case "BX": return registry[1] % 0x1_0000;
			case "CX": return registry[2] % 0x1_0000;
			case "DX": return registry[3] % 0x1_0000;
			
			case "DI": return registry[4] % 0x1_0000;
			case "SI": return registry[5] % 0x1_0000;
			
			case "SP": return registry[6] % 0x1_0000;
			case "BP": return registry[7] % 0x1_0000;
			
			case "IP":    return registry[8] % 0x1_0000;
			case "FLAGS": return registry[9] % 0x1_0000;
			
			// 8-bit registers
			case "AH": return registry[0] % 0x1_0000 / 0x100;
			case "AL": return registry[0] % 0x100;
			case "BH": return registry[1] % 0x1_0000 / 0x100;
			case "BL": return registry[1] % 0x100;
			case "CH": return registry[2] % 0x1_0000 / 0x100;
			case "CL": return registry[2] % 0x100;
			case "DH": return registry[3] % 0x1_0000 / 0x100;
			case "DL": return registry[3] % 0x100;
			
			default: {
				// TODO Invalid register
				// Check to see if it's an identifier
				return data.get(x);
			}
		}
	}
	
	/**
	 * Writes an unsigned value to the register x
	 * @param x
	 * @param value
	 */
	private void write(String x, int value) {
		
		switch(x.toUpperCase()) {
		
			// 32-bit data registers
			case "EAX": registry[0] = value; break; // Primary accumulator, I/O and arithmetic
			case "EBX": registry[1] = value; break; // Base register, indexing                
			case "ECX": registry[2] = value; break; // Count register, loop counters          
			case "EDX": registry[3] = value; break; // Data register, I/O and arithmetic      
			// 32-bit index registers
			case "EDI": registry[4] = value; break; // Source Index, string operations
			case "ESI": registry[5] = value; break; // Destination Index, string operations
			// 32-bit pointer registers
			case "ESP": registry[6] = value; break; // Stack pointer
			case "EBP": registry[7] = value; break; // Base pointer
			// 32-bit control registers
			case "EIP":    registry[8] = value; break;
			case "EFLAGS": registry[9] = value; break;
			
			// 16-bit registers
			case "AX": registry[0] = registry[0] / 0x1_0000 + value % 0x1_0000; break;
			case "BX": registry[1] = registry[1] / 0x1_0000 + value % 0x1_0000; break;
			case "CX": registry[2] = registry[2] / 0x1_0000 + value % 0x1_0000; break;
			case "DX": registry[3] = registry[3] / 0x1_0000 + value % 0x1_0000; break;
			
			case "DI": registry[4] = registry[4] / 0x1_0000 + value % 0x1_0000; break;
			case "SI": registry[5] = registry[5] / 0x1_0000 + value % 0x1_0000; break;
			
			case "SP": registry[6] = registry[6] / 0x1_0000 + value % 0x1_0000; break;
			case "BP": registry[7] = registry[7] / 0x1_0000 + value % 0x1_0000; break;
			
			case "IP":    registry[8] = registry[8] / 0x1_0000 + value % 0x1_0000; break;
			case "FLAGS": registry[9] = registry[9] / 0x1_0000 + value % 0x1_0000; syncFlags(); break;
			
			// 8-bit registers
			case "AH": registry[0] = registry[0] / 0x1_0000 + value % 0x100 * 0x100 + registry[0] % 0x100; break;
			case "AL": registry[0] = registry[0] / 0x100 + value % 0x100; break;
			case "BH": registry[1] = registry[1] / 0x1_0000 + value % 0x100 * 0x100 + registry[1] % 0x100; break;
			case "BL": registry[1] = registry[1] / 0x100 + value % 0x100; break;
			case "CH": registry[2] = registry[2] / 0x1_0000 + value % 0x100 * 0x100 + registry[2] % 0x100; break;
			case "CL": registry[2] = registry[2] / 0x100 + value % 0x100; break;
			case "DH": registry[3] = registry[3] / 0x1_0000 + value % 0x100 * 0x100 + registry[3] % 0x100; break;
			case "DL": registry[3] = registry[3] / 0x100 + value % 0x100; break;
			
			default: {
				// TODO Invalid register
				// Check to see if it's an identifier
				data.put(x, value);
			}
		}
	}
	
	/**
	 * Updates flags to the current value of the EFLAGS register.
	 */
	private void syncFlags() {
		int flags = registry[9];
		carryFlag 	  = flags % 2 == 1; flags /= 4;
		parityFlag 	  = flags % 2 == 1; flags /= 4;
		adjustFlag 	  = flags % 2 == 1; flags /= 4;
		zeroFlag 	  = flags % 2 == 1; flags /= 2;
		signFlag 	  = flags % 2 == 1; flags /= 2;
		trapFlag 	  = flags % 2 == 1; flags /= 2;
		interruptFlag = flags % 2 == 1; flags /= 2;
		directionFlag = flags % 2 == 1; flags /= 2;
		overflowFlag  = flags % 2 == 1;
	}

	/**
	 * Copy y (either an integer or the value of a register) into register x.
	 * @param x
	 * @param y
	 */
	private void move(String x, String y) {
		write(x, valueOf(y));
	}
	
	/**
	 * Increase the content of register x by one.
	 * @param x
	 */
	private void increment(String x) {
		write(x, read(x) + 1);
	}

	/**
	 * Decrease the content of register x by one.
	 * @param x
	 */
	private void decrement(String x) {
		write(x, read(x) - 1);
	}
	
	/**
	 * Add the content of the register x with y (either an integer or the value of a register) and stores the result in x (i.e. register[x] += y).
	 * @param x
	 * @param y
	 */
	private void add(String x, String y) {
		write(x, read(x) + valueOf(y));
	}
	
	/**
	 * Subtract y (either an integer or the value of a register) from the register x and stores the result in x (i.e. register[x] -= y).
	 * @param x
	 * @param y
	 */
	private void subtract(String x, String y) {
		write(x, read(x) - valueOf(y));
	}
	
	/**
	 * Same with multiply (i.e. register[x] *= y).
	 * @param x
	 * @param y
	 */
	private void multiply(String x, String y) {
		write(x, read(x) * valueOf(y));
	}
	
	/**
	 * Same with integer division (i.e. register[x] /= y).
	 * @param x
	 * @param y
	 */
	private void divide(String x, String y) {
		write(x, read(x) / valueOf(y));
	}
	
	/**
	 * Divide register x by y and store the remainder in x.
	 * @param x
	 * @param y
	 */
	private void mod(String x, String y) {
		write(x, read(x) % valueOf(y));
	}
	
	/**
	 * Jumps to the label.
	 * @param label
	 * @return
	 */
	private int jump(String label) {
		return data.get(label) - 1;
	}
	
	/**
	 * Transfers the low byte of the flags word to the AH register.
	 * The bits (lsb to msb) are: sign, zero, indeterminate, auxiliary carry, indeterminate, parity, indeterminate, and carry.
	 */
	private void loadFlagsIntoAHRegister() {
		pushFlagRegisterOntoStack("AH");
	}
	
	/**
	 * Loads flags (sign, zero, indeterminate, auxiliary carry, indeterminate, parity, indeterminate, and carry) with values from the AH register.
	 */
	private void storeAHIntoFlags() {
		popStackIntoFlags("AH");
	}
	
	/**
	 * Pops the word or long from the top of the stack and stores the value in the flags register.
	 * Stores a word in FLAGS; stores a long in EFLAGS.
	 * @param x
	 */
	private void popStackIntoFlags(String x) {
		write("EFLAGS", read(x));
		syncFlags();
	}
	
	/**
	 * For a word, SP - 2 and copies FLAGS to the new top of stack pointed to by SP.
	 * For a long, SP - 4 and copies EFLAGS to the new top of stack pointed to by SS:eSP.
	 * @param x
	 */
	private void pushFlagRegisterOntoStack(String x) {
		write(x, read("EFLAGS"));
	}

	/**
	 * Reverses the setting of the carry flag; affects no other flags.
	 */
	private void complementCarryFlag() {
		registry[9] += (registry[9] % 2 == 0) ? 1 : -1;
		carryFlag = !carryFlag;
	}
	
	/**
	 * Sets the carry flag to zero; affects no other flags.
	 */
	private void clearCarryFlag() {
		if(registry[9] % 2 == 1) registry[9]--;
		carryFlag = false;
	}
	
	/**
	 * Sets the carry flag to 1.
	 */
	private void setCarryFlag() {
		if(registry[9] % 2 == 0) registry[9]++;
		carryFlag = true;
	}
	
	/**
	 * Clears the interrupt flag if the current privilege level is at least as privileged as IOPL; affects no other flags.
	 * External interrupts disabled at the end of the cli instruction or from that point on until the interrupt flag is set.
	 */
	private void clearInterruptFlag() {
		if(registry[9] / 0x200 % 2 == 1)
			registry[9] -= 0x200; 
		interruptFlag = false;
	}
	
	/**
	 * Sets the interrupt flag to 1.
	 */
	private void setInterruptFlag() {
		if(registry[9] / 0x200 % 2 == 0)
			registry[9] += 0x200; 
		interruptFlag = true;
	}
	
	/**
	 * Clears the direction flag; affects no other flags or registers.
	 * Causes all subsequent string operations to increment the index registers, (E)SI and/or (E)DI, used during the operation.
	 */
	private void clearDirectionFlag() {
		if(registry[9] / 0x400 % 2 == 1)
			registry[9] -= 0x400; 
		directionFlag = false;
		
	}
	
	/**
	 * Sets the direction flag to 1, causing all subsequent string operations to decrement the index registers, (E)SI and/or (E)DI, used during the operation.
	 */
	private void setDirectionFlag() {
		if(registry[9] / 0x400 % 2 == 0)
			registry[9] += 0x400; 
		directionFlag = true;		
	}

	/**
	 * The bitwise AND operation returns 1, if the matching bits from both the operands are 1, otherwise it returns 0.
	 * @param x
	 * @param y
	 */
	private void and(String x, String y) {
		write(x, read(x) & read(y));
	}
	
	/**
	 * The bitwise OR operator returns 1, if the matching bits from either or both operands are one.
	 * It returns 0, if both the bits are zero.
	 * @param x
	 * @param y
	 */
	private void or(String x, String y) {
		write(x, read(x) | read(y));
	}
	
	/**
	 * The bitwise XOR operation sets the resultant bit to 1, if and only if the bits from the operands are different.
	 * If the bits from the operands are same (both 0 or both 1), the resultant bit is cleared to 0.
	 * @param x
	 * @param y
	 */
	private void exclusiveOr(String x, String y) {
		write(x, read(x) ^ read(y));		
	}

	/**
	 * Prints out the parameters of the given statement.
	 * @param statement
	 */
	private void print(String[] statement) {
		for(int i = 1; i < statement.length; i++)
			if(statement[i].contains("\'"))
				output += statement[i].substring(1, statement[i].length() - 1);
			else output += valueOf(statement[i]);
	}
	
	/**
	 * Stores the comparison between x and y in the comparison register.
	 * @param x
	 * @param y
	 */
	private void compare(String x, String y) {
		compare = Integer.compare(valueOf(x), valueOf(y));
	}
	
	/**
	 * Determines if the given string is a number or an identifier, and returns its value.
	 * @param s
	 * @return
	 */
	private Integer valueOf(String s) {
		try {
			return Integer.parseInt(s);
		} catch(NumberFormatException nfe) {
			return read(s);
		}
	}
    
}
