import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;

public class AssemblerInterpreter {
	
    public static String interpret(final String input) {
      
        // convert input into a list of lines
        ArrayList<String> program = new ArrayList<String>(Arrays.asList(input.split("\n")));
        
        HashMap<String, Integer> labels = new HashMap<String, Integer>();
        LinkedList <Integer> stack = new LinkedList<Integer>();
        HashMap<String, Integer> registry = new HashMap<String, Integer>();
        int compare = 0;
        String output = new String();
      
        for(int i = 0; i < program.size(); i++) {
            // retrieve instruction line
            String instruction = program.get(i);
            // purge comments
            if(instruction.contains(";"))
                instruction = instruction.substring(0, instruction.indexOf(';'));
            // remove leading/trailing whitespaces
            instruction = instruction.trim();
            // remove excess spaces between parameters, excluding String literals
            instruction.replaceAll("[\\s]+(?=[^']*(?:'[^']*'[^']*)*$)", " ");
          
            if(instruction.contains(":")) {
                String temp = instruction.split(" ")[0];
                if(temp.endsWith(":")) {
                    labels.put(instruction.substring(0, instruction.indexOf(':')), i);
                    instruction = instruction.substring(temp.length());
                }
            }
            
            program.set(i, instruction);
            
        }
      
        // Execute Program
        for(int i = 0; i < program.size(); i++) {
            
            // Split statement by spaces 
            String[] statement = program.get(i).split("[, ]+(?=[^']*(?:'[^']*'[^']*)*$)");
            
            switch(statement[0]) {
                
                case "mov": registry.put(statement[1], valueOf(registry, statement[2])); break;
                
                case "inc": registry.put(statement[1], registry.get(statement[1]) + 1); break;
                case "dec": registry.put(statement[1], registry.get(statement[1]) - 1); break;
                
                case "add": registry.put(statement[1], registry.get(statement[1]) + valueOf(registry, statement[2])); break;
                case "sub": registry.put(statement[1], registry.get(statement[1]) - valueOf(registry, statement[2])); break;
                case "mul": registry.put(statement[1], registry.get(statement[1]) * valueOf(registry, statement[2])); break;
                case "div": registry.put(statement[1], registry.get(statement[1]) / valueOf(registry, statement[2])); break;
                
                case "jmp": i = labels.get(statement[1]) - 1; break;
                case "cmp": compare = valueOf(registry, statement[1]) - valueOf(registry, statement[2]); break;
                case "jne": if(compare != 0) i = labels.get(statement[1]) - 1; break;
                case "je":  if(compare == 0) i = labels.get(statement[1]) - 1; break;
                case "jge": if(compare >= 0) i = labels.get(statement[1]) - 1; break;
                case "jg":  if(compare > 0)  i = labels.get(statement[1]) - 1; break;
                case "jle": if(compare <= 0) i = labels.get(statement[1]) - 1; break;
                case "jl":  if(compare < 0)  i = labels.get(statement[1]) - 1; break;
                
                case "call": stack.add(i); i = labels.get(statement[1]) - 1; break;
                case "ret": i = stack.removeLast(); break;
                
                case "msg": for(int j = 1; j < statement.length; j++) output += (statement[j].contains("'"))
                			? statement[j].substring(1, statement[j].length() - 1) 
                			: valueOf(registry, statement[j]); break;
                
                case "end": return output;
                
            }
            
        }
      
        return null;
      
    }
  
    private static int valueOf(HashMap<String, Integer> registry, String string) {
        try {
            return Integer.parseInt(string);
        } catch(NumberFormatException nfe) {
            return registry.get(string);
        }
    }
    
}
