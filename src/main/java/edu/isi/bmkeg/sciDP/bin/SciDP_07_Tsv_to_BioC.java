package edu.isi.bmkeg.sciDP.bin;

import java.io.File;

import org.apache.log4j.Logger;
import org.apache.uima.collection.CollectionProcessingEngine;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.collection.StatusCallbackListener;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.cleartk.opennlp.tools.SentenceAnnotator;
import org.cleartk.token.tokenizer.TokenAnnotator;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.uimafit.factory.AggregateBuilder;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.CollectionReaderFactory;
import org.uimafit.factory.CpeBuilder;
import org.uimafit.factory.TypeSystemDescriptionFactory;

import edu.isi.bmkeg.sciDP.uima.ae.InsertTsvBackIntoBioC;
import edu.isi.bmkeg.uimaBioC.rubicon.RemoveSentencesFromOtherSections;
import edu.isi.bmkeg.uimaBioC.rubicon.RemoveSentencesNotInTitleAbstractBody;
import edu.isi.bmkeg.uimaBioC.uima.ae.core.FixSentencesFromHeadings;
import edu.isi.bmkeg.uimaBioC.uima.out.SaveAsBioCDocuments;
import edu.isi.bmkeg.uimaBioC.uima.readers.BioCCollectionReader;
import edu.isi.bmkeg.uimaBioC.utils.StatusCallbackListenerImpl;

public class SciDP_07_Tsv_to_BioC {

	public static class Options {

		@Option(name = "-nThreads", usage = "Number of threads", required = true, metaVar = "N-THREADS")
		public int nThreads;

		@Option(name = "-tsvDir", usage = "Input Directory", required = true, metaVar = "IN-DIRECTORY")
		public File tsvDir;

		@Option(name = "-bioCDir", usage = "BioC Directory", required = true, metaVar = "BIOC-DIRECTORY")
		public File biocDir;
		
		@Option(name = "-outDir", usage = "Output Directory", required = true, metaVar = "OUT-DIRECTORY")
		public File outDir;

		@Option(name = "-outFormat", usage = "Output Format", required = true, metaVar = "OUT-FORMAT")
		public String outFormat;
		
		@Option(name = "-ann2Extract", usage = "Annotation Type to Extract", required = false, metaVar = "ANNOTATION")
		public String ann2Ext;

	}

	private static Logger logger = Logger.getLogger(SciDP_07_Tsv_to_BioC.class);

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

		TypeSystemDescription typeSystem = TypeSystemDescriptionFactory.createTypeSystemDescription("bioc.TypeSystem");

		CollectionReaderDescription crDesc = CollectionReaderFactory.createDescription(BioCCollectionReader.class,
				typeSystem, BioCCollectionReader.INPUT_DIRECTORY, options.biocDir.getPath(),
				BioCCollectionReader.OUTPUT_DIRECTORY, options.outDir.getPath(), BioCCollectionReader.PARAM_FORMAT,
				BioCCollectionReader.JSON);

		CpeBuilder cpeBuilder = new CpeBuilder();
		cpeBuilder.setReader(crDesc);

		AggregateBuilder builder = new AggregateBuilder();

		builder.add(SentenceAnnotator.getDescription()); // Sentence
		builder.add(TokenAnnotator.getDescription()); // Tokenization

		//
		// Some sentences include headers that don't end in periods
		//
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(FixSentencesFromHeadings.class));

		//
		// Strip out not results sections where we aren't interested in them
		//
		if( options.ann2Ext != null ) {
			builder.add(AnalysisEngineFactory.createPrimitiveDescription(RemoveSentencesFromOtherSections.class,
					RemoveSentencesFromOtherSections.PARAM_ANNOT_2_EXTRACT, options.ann2Ext,
					RemoveSentencesFromOtherSections.PARAM_KEEP_FLOATING_BOXES, "false"));
		} else {
			builder.add(AnalysisEngineFactory.createPrimitiveDescription(RemoveSentencesNotInTitleAbstractBody.class,
					RemoveSentencesNotInTitleAbstractBody.PARAM_KEEP_FLOATING_BOXES, "false"));
		}

		builder.add(AnalysisEngineFactory.createPrimitiveDescription(InsertTsvBackIntoBioC.class,
				InsertTsvBackIntoBioC.PARAM_INPUT_DIRECTORY, options.tsvDir.getPath(),
				InsertTsvBackIntoBioC.PARAM_KEEP_FLOATING_BOXES, "false",
				InsertTsvBackIntoBioC.PARAM_ANNOT_2_EXTRACT, options.ann2Ext));

		String outFormat = null;
		if( options.outFormat.toLowerCase().equals("xml") ) 
			outFormat = SaveAsBioCDocuments.XML;
		else if( options.outFormat.toLowerCase().equals("json") ) 
			outFormat = SaveAsBioCDocuments.JSON;
		else 
			throw new Exception("Output format " + options.outFormat + " not recognized");
	
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(
				SaveAsBioCDocuments.class, 
				SaveAsBioCDocuments.PARAM_FILE_PATH,
				options.outDir.getPath(),
				SaveAsBioCDocuments.PARAM_FORMAT,
				outFormat));
		
		cpeBuilder.setAnalysisEngine(builder.createAggregateDescription());

		cpeBuilder.setMaxProcessingUnitThreatCount(options.nThreads);
		StatusCallbackListener callback = new StatusCallbackListenerImpl();
		CollectionProcessingEngine cpe = cpeBuilder.createCpe(callback);
		System.out.println("Running CPE");
		cpe.process();

		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
		}

		while (cpe.isProcessing())
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
			}

		System.out.println("\n\n ------------------ PERFORMANCE REPORT ------------------\n");
		System.out.println(cpe.getPerformanceReport().toString());

		long endTime = System.currentTimeMillis();
		float duration = (float) (endTime - startTime);
		System.out.format("\n\nTOTAL EXECUTION TIME: %.3f s", duration / 1000);

	}

}
