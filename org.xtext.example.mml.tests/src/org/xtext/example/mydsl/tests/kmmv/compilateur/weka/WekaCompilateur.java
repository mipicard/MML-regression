package org.xtext.example.mydsl.tests.kmmv.compilateur.weka;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.xtext.example.mydsl.mml.AllVariables;
import org.xtext.example.mydsl.mml.DataInput;
import org.xtext.example.mydsl.mml.MLChoiceAlgorithm;
import org.xtext.example.mydsl.mml.PredictorVariables;
import org.xtext.example.mydsl.mml.RFormula;
import org.xtext.example.mydsl.mml.Validation;
import org.xtext.example.mydsl.tests.kmmv.compilateur.Compilateur;
import org.xtext.example.mydsl.tests.kmmv.compilateur.Pair;
import org.xtext.example.mydsl.tests.kmmv.compilateur.Utils;

/**
 * List of key word accross the compilateur :
 * -> clf : algorithm
 * -> data : data
 */
public class WekaCompilateur implements Compilateur {

	@Override
	public String compile(DataInput input, MLChoiceAlgorithm algorithm, RFormula formula, Validation validation, int uniqueId) {
		Pair<List<String>, List<String>> inputC = compileDataInput(input);
		Pair<List<String>, List<String>> formulaC = compileRFormula(formula);
		Pair<List<String>, List<String>> algorithmC = MLAlgorithmCompiler.compile(algorithm.getAlgorithm());
		Pair<List<String>, List<String>> validationC = ValidationCompiler.compile(validation);
		
		List<String> buffer = new LinkedList<>(), bufferImports = new LinkedList<>();
		
		bufferImports.addAll(inputC.getFirst());
		bufferImports.addAll(formulaC.getFirst());
		bufferImports.addAll(algorithmC.getFirst());
		bufferImports.addAll(validationC.getFirst());
		
		buffer.addAll(filterImport(bufferImports));
		buffer.add("");
		buffer.add(String.format("public class %s {", className(input, algorithm, validation, uniqueId)));
		buffer.add(String.format("%spublic static void main(String[] arg) throws Exception {", Utils.tab()));
		buffer.addAll(Utils.insertTab(inputC.getSecond(),2));
		buffer.addAll(Utils.insertTab(formulaC.getSecond(),2));
		buffer.addAll(Utils.insertTab(algorithmC.getSecond(),2));
		buffer.addAll(Utils.insertTab(validationC.getSecond(),2));
		buffer.add(String.format("%s}\n}",Utils.tab()));
		
		return String.join("\n", buffer);
	}

	@Override
	public String fileName(DataInput input, MLChoiceAlgorithm algorithm, Validation validation, int uniqueId) {
		return String.format("%s.java", className(input, algorithm, validation, uniqueId));
	}
	
	private String className(DataInput input, MLChoiceAlgorithm algorithm, Validation validation, int uniqueId) {
		return String.format("%s_%s_%s_%s", input.getFilelocation().replace(".", "_"), Utils.algorithmName(algorithm.getAlgorithm()), Utils.stratificationToString(validation.getStratification()), uniqueId);
	}

	private Pair<List<String>, List<String>> compileDataInput(DataInput input) {
		List<String> import_ = new LinkedList<>();
		List<String> code_ = new LinkedList<>();
		
		import_.add("import weka.core.converters.ConverterUtils.DataSource;");
		import_.add("import weka.core.Instances;");
		
		code_.add(String.format("Instances data = DataSource.read(\"%s\");", input.getFilelocation()));
		
		return new Pair<>(import_, code_);
	}
	
	private Pair<List<String>, List<String>> compileRFormula(RFormula formula){
		List<String> import_ = new LinkedList<>();
		List<String> code_ = new LinkedList<>();
		
		import_.add("import weka.core.Attribute;");
		import_.add("import java.util.List;");
		import_.add("import java.util.LinkedList;");
		import_.add("import weka.core.Instances;");
		import_.add("import java.util.Enumeration;");
		
		code_.add("List<String> column = new LinkedList<>();");
		code_.add("String className = \"\";");
		code_.add("for(Enumeration<Attribute> atr = data.enumerateAttributes(); atr.hasMoreElements();)");
		code_.add(String.format("%scolumn.add(atr.nextElement().name());", Utils.tab()));
		
		if(formula != null && formula.getPredictive() != null) {
			if(formula.getPredictive().getColName() != null)
				code_.add(String.format("className = \"%s\";", formula.getPredictive().getColName()));
			else
				code_.add(String.format("className = column.get(%d);", formula.getPredictive().getColumn()));
		} else {
			code_.add("className = column.get(column.size()-1);");
		}
		
		code_.add("data.setClassIndex(column.indexOf(className));");
		
		if(formula != null && formula.getPredictors() != null && !(formula.getPredictors() instanceof AllVariables)) {			
			List<String> predictorsName = ((PredictorVariables) formula.getPredictors())
					.getVars()
					.stream()
					.filter(variable -> variable.getColName() != null && !variable.getColName().isBlank())
					.map(variable -> String.format("\"%s\"", variable.getColName()))
					.collect(Collectors.toList());
			List<Integer> predictorsIndex = ((PredictorVariables) formula.getPredictors())
					.getVars()
					.stream()
					.filter(variable -> variable.getColName() == null || variable.getColName().isBlank())
					.map(variable -> variable.getColumn())
					.collect(Collectors.toList());
			
			import_.add("import java.util.Collections;");
			import_.add("import java.util.Set;");
			import_.add("import java.util.HashSet;");
			import_.add("import java.util.stream.Collectors;");
			
			code_.add("Set<String> columnToKeep = new HashSet<>();");
			for(String predN : predictorsName)
				code_.add(String.format("columnToKeep.add(%s);", predN));
			for(Integer predI : predictorsIndex)
				code_.add(String.format("columnToKeep.add(column.get(%d));", predI));
			code_.add("if(!columnToKeep.contains(className))");
			code_.add(String.format("%scolumnToKeep.add(className);",Utils.tab()));
			code_.add("List<Integer> removeColumn = column.stream().filter(name -> !columnToKeep.contains(name)).map(name -> column.indexOf(name)).collect(Collectors.toList());");
			code_.add("Collections.reverse(removeColumn);");
			code_.add("for(Integer index : removeColumn)");
			code_.add(String.format("%sdata.deleteAttributeAt(index);", Utils.tab()));
		}
		
		return new Pair<>(import_, code_);
	}
	
	private List<String> filterImport(List<String> imports) {
		List<String> result = new LinkedList<>();
		
		for(String imp : imports) {
			if(!result.contains(imp))
				result.add(imp);
		}
		
		return result;
	}

	@Override
	public String commandLine(String file) {
		return String.format("javac -cp weka.jar %s && java -cp .:weka.jar %s", file, file.substring(0, file.length()-5));
	}
}
