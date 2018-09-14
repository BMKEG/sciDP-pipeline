package edu.isi.bmkeg.sciDT.bin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jena.ontology.Individual;
import org.apache.jena.ontology.ObjectProperty;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.log4j.Logger;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

public class SciDT_09_ConvertBioPaxToLinkedData {

	public static class Options {

		@Option(name = "-biopaxDir", usage = "Input Biopax Directory", required = true, metaVar = "IN-DIRECTORY")
		public File biopaxDir;

		@Option(name = "-outDir", usage = "Temporary Directory for intermediate files", required = true, metaVar = "TEMP-DIR")
		public File outDir;

		@Option(name = "-outFile", usage = "Output BioC Collection File", required = true, metaVar = "OUT-FILE")
		public File outFile;

	}

	private static Logger logger = Logger.getLogger(SciDT_09_ConvertBioPaxToLinkedData.class);

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		long startTime = System.currentTimeMillis();

		Options options = new Options();

		CmdLineParser parser = new CmdLineParser(options);

		try {

			parser.parseArgument(args);

		} catch (CmdLineException e) {

			System.err.println(e.getMessage());
			System.err.print("Arguments: ");
			parser.printSingleLineUsage(System.err);
			System.err.println("\n\n Options: \n");
			parser.printUsage(System.err);
			System.exit(-1);

		}
		
		if(!options.outDir.exists()) 
			options.outDir.mkdirs();
		
		String[] fileTypes = { "xml", "rdf", "txt", "json", "tsv" };
		Collection<File> fileList = (Collection<File>) FileUtils.listFiles(new File(options.biopaxDir.getPath()),
				fileTypes, true);
		for (File f : fileList) {
			
			OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
			ontModel.setNsPrefix("cito", "http://purl.org/spar/cito/");
			ontModel.setNsPrefix("", "file://"+options.biopaxDir.getPath()+"/");
			ObjectProperty describes_property = ontModel.createObjectProperty("http://purl.org/spar/cito/describes");
			OntClass figureLabel_class = ontModel.createClass("http://purl.org/spar/doco/FigureLabel");
			
			String fName = f.getName().replaceAll("(.xml|.rdf)", ".ttl");
			String fileStem = f.getPath().substring(0,f.getPath().length()-f.getName().length());
			logger.info(fName);
			
			File outFile = new File(options.outDir.getPath() + "/" + fName);
			if( outFile.exists() ){
				logger.info("	Skipping");
				continue;
			}
			
			ontModel.read(f.getPath(), "TTL");

			String queryString = "PREFIX biopax: <http://www.biopax.org/release/biopax-level3.owl#> "
					+ "SELECT ?ev ?pmid ?figCode" + " WHERE { " + "?ev a biopax:Evidence ." + "?ev biopax:xref ?xref ."
					+ "?xref biopax:db 'pubmed' ." + "?xref biopax:id ?pmid ." + "?ev biopax:comment ?figCode ."
					+ "FILTER regex(?figCode, \"^Figure:\", \"i\")" + "}";

			Map<Resource, List<Individual>> resultMap = new HashMap<Resource, List<Individual>>();
			Query query = QueryFactory.create(queryString);
			try (QueryExecution qexec = QueryExecutionFactory.create(query, ontModel)) {
				ResultSet results = qexec.execSelect();
				for (; results.hasNext();) {
					QuerySolution soln = results.nextSolution();
					Resource ev = soln.getResource("ev");
					Literal pmid_lit = soln.getLiteral("pmid");
					Literal figCode_lit = soln.getLiteral("figCode");

					String pmid = pmid_lit.getString();
					String figCode = figCode_lit.getString();
					figCode = figCode.replaceAll("Figure:", "");
					figCode = figCode.replaceAll("\\s+", "_");
					figCode = figCode.replaceAll("\\&", "+").toLowerCase();
					if( !figCode.startsWith("f") )
						figCode += "f"+figCode;
					List<Individual> figRefList = new ArrayList<Individual>(); 
					for( String fc : figCode.split("\\|")) {
						Individual figRef = ontModel.createIndividual(
								"http://www.ncbi.nlm.nih.gov/pubmed/" + pmid + "#" + fc,
								figureLabel_class);
						figRefList.add(figRef);
					}
					resultMap.put(ev, figRefList);
					
				}
				
			}
			
			// Need a separate loop to avoid ConcurrentModificationException
			for( Resource ev : resultMap.keySet() ){
				for( Individual figRef : resultMap.get(ev)){
					ontModel.add(ev, describes_property, figRef);
				}
			}
			
			PrintWriter out;
			try {
			
				out = new PrintWriter(new BufferedWriter(
						new FileWriter(outFile, true)));
				ontModel.write(out, "TTL");
				out.close();
				
				FileInputStream is = new FileInputStream(outFile);     
				String ttlContents = IOUtils.toString(is);
				is.close();
				
				String newTtl = ttlContents.replaceAll("file://"+fileStem, "http://purl.org/ske/semsci17/");
				outFile.delete();
				out = new PrintWriter(new BufferedWriter(
						new FileWriter(outFile, true)));
				out.write(newTtl);
				out.close();
				
			} catch (IOException e) {
				throw new AnalysisEngineProcessException(e);
			}
	
		}

	}

}
