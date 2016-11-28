package edu.isi.bmkeg.sciDP.bin;

import java.io.File;
import java.util.Arrays;

import org.apache.log4j.Logger;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import edu.isi.bmkeg.uimaBioC.bin.UIMABIOC_03_BioCToClauseTsv;

/**
 * This script runs through serialized JSON files from the model and converts
 * them to VPDMf KEfED models, including the data.
 * 
 * @author Gully
 * 
 */
public class SciDP_1_SciDP2Tsv {

	public static class Options {

		@Option(name = "-inDir", usage = "Input Directory", required = true, metaVar = "INPUT")
		public File inDir;

		@Option(name = "-nThreads", usage = "Number of threads", required = true, metaVar = "IN-DIRECTORY")
		public int nThreads;
		
	}

	private static Logger logger = Logger
			.getLogger(SciDP_1_SciDP2Tsv.class);

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
				"-bioCDir", options.inDir + "/preprocessed_bioc",
				"-nThreads", options.nThreads + "",
				"-outDir", options.inDir + "/scidp_bioc",
				"-outFormat", "json",
				"-sciDPDir", options.inDir + "/scidp"};
	
		SciDP_06_sciDP_to_BioC.main(args00);
		
		String[] args01 = new String[] { 
				"-biocDir", options.inDir + "/scidp_bioc",
				"-nThreads", options.nThreads + "",
				"-outDir", options.inDir + "/tsv"
				};
		
		UIMABIOC_03_BioCToClauseTsv.main(args01);
				
	}

}