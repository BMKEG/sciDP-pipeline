package edu.isi.bmkeg.sciDP.bin;

import java.io.File;
import java.util.Arrays;

import org.apache.log4j.Logger;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import edu.isi.bmkeg.uimaBioC.bin.UIMABIOC_00_SimpleRunNxml2Txt;
import edu.isi.bmkeg.uimaBioC.bin.UIMABIOC_01_Nxml2txt_to_BioC;
import edu.isi.bmkeg.uimaBioC.bin.UIMABIOC_02_preprocessToBioC;
import edu.isi.bmkeg.uimaBioC.bin.UIMABIOC_03_BioCToClauseTsv;

/**
 * This script runs through serialized JSON files from the model and converts
 * them to VPDMf KEfED models, including the data.
 * 
 * @author Gully
 * 
 */
public class SciDP_0_Nxml2SciDP {

	public static class Options {

		@Option(name = "-inDir", usage = "Input Directory", required = true, metaVar = "INPUT")
		public File inDir;

		@Option(name = "-nxml2textPath", usage = "Path to the nxml2text executable", required = true, metaVar = "PATH")
		public File nxml2textPath;

		@Option(name = "-nThreads", usage = "Number of threads", required = true, metaVar = "IN-DIRECTORY")
		public int nThreads;

		@Option(name = "-maxSentenceLength", usage = "Maximum length of sentences to be parsed", required = false, metaVar = "MAX-PARSE-LENGTH")
		public int maxSentenceLength;

		@Option(name = "-friesDir", usage = "Fries Directory", required = false, metaVar = "FRIES-DATA")
		public File friesDir;
		
	}

	private static Logger logger = Logger
			.getLogger(SciDP_0_Nxml2SciDP.class);

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

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
		
		String[] args00 = new String[] { 
				"-inDir", options.inDir + "/nxml", 
				"-outDir", options.inDir + "/nxml2txt",
				"-execPath", options.nxml2textPath.getPath()
				};
		UIMABIOC_00_SimpleRunNxml2Txt.main(args00);
		
		String[] args01 = new String[] { 
				"-inDir", options.inDir + "/nxml2txt", 
				"-outDir", options.inDir + "/bioc",
				"-outFormat", "json"
				};
		UIMABIOC_01_Nxml2txt_to_BioC.main(args01);
		
		String[] args02 = new String[] { 
				"-biocDir", options.inDir + "/bioc", 
				"-outDir", options.inDir + "/preprocessed_bioc",
				"-outFormat", "json",
				"-nThreads", options.nThreads + "",
				"-maxSentenceLength", options.maxSentenceLength + ""
				};
		if( options.friesDir != null) {
			args02 = Arrays.copyOf(args02, args02.length+2);
			args02[args02.length-2] = "-friesDir"; 
			args02[args02.length-1] = options.friesDir.getPath(); 
		}
		UIMABIOC_02_preprocessToBioC.main(args02);
		
		String[] args03 = new String[] { 
				"-biocDir", options.inDir + "/preprocessed_bioc", 
				"-outDir", options.inDir + "/scidp",
				"-nThreads", options.nThreads + "",
				};
		SciDP_03_prepareData.main(args03);

		String[] args04 = new String[] { 
				"-biocDir", options.inDir + "/preprocessed_bioc",
				"-nThreads", options.nThreads + "",
				"-outDir", options.inDir + "/tsv"
				};
		
		UIMABIOC_03_BioCToClauseTsv.main(args04);

	}

}