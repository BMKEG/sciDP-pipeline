package edu.isi.bmkeg.sciDP.bin;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

/**
 * This script trains the sciDP neural net classifier on a set of files 
 * (we will need to set up the input and output of the data for this):
 * It does matter where you run this script from, so not sure how to execute in the context of this
 * 
 * python /usr1/shared/projects/bigmech/tools/sciDP/nn_passage_tagger.py 
 * 		/usr1/shared/projects/bigmech/data/embeddings/pyysalo_et_al/wikipedia-pubmed-and-PMC-w2v.txt.gz 
 * 		--use_attention 
 *      --train /usr1/shared/projects/bigmech/data/discourse_tagging/train+test_data/passage_train.txt 
 * 
 * @author Gully
 * 
 */
public class SciDP_04_trainSciDP {

	public static class Options {

		@Option(name = "-nnTaggerPath", usage = "Path to the nn_passage_tagger script", required = true, metaVar = "PATH")
		public File taggerPath;

		@Option(name = "-modelPath", usage = "Path to the word2vec embeddings", required = true, metaVar = "MODEL")
		public File modelPath;

		@Option(name = "-trainPath", usage = "Path to the training files", required = true, metaVar = "TRAIN")
		public File trainPath;

		@Option(name = "-pythonPath", usage = "Path to the python executable", required = true, metaVar = "TRAIN")
		public File pythonPath;

		@Option(name = "-suffix", usage = "Altered suffix of *.nxml files", required = false, metaVar = "SCIDP SUFFIX")
		public String suffix = "_scidp.txt";
		
	}

	private static Logger logger = Logger
			.getLogger(SciDP_05_runSciDP.class);

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

		Pattern pattern = Pattern.compile("\\.(.*)$");
		Matcher matcher = pattern.matcher(options.suffix);
		String fileEx = options.suffix;
		if( matcher.find() )
			fileEx = matcher.group(1);
		
		String[] fileTypes = {fileEx};
		
		String command = options.taggerPath.getPath() 
						+ " " + options.modelPath
						+ " --use_attention --train "
						+ options.trainPath;
				
		runPythonCommand(command, options.pythonPath, options.trainPath.getParentFile());
			
	}
	
	private static void runPythonCommand(String command, File pythonPath, File trainDir) throws Exception {

		command = pythonPath.getPath() + "/bin/python " + command;
		
		ProcessBuilder pb = new ProcessBuilder(command.split(" "));
		Map<String,String> env = pb.environment();
		env.put("PYTHONPATH", pythonPath.getPath() + "/lib/python2.7/site-packages");
		pb.directory(trainDir);
		Process p = pb.start();
		
		if (p == null) {
			throw new Exception("Can't execute " + command);
		}

		InputStream in = p.getErrorStream();
		BufferedInputStream buf = new BufferedInputStream(in);
		InputStreamReader inread = new InputStreamReader(buf);
		BufferedReader bufferedreader = new BufferedReader(inread);
		String line, out = "";

		while ((line = bufferedreader.readLine()) != null) {
			out += line;
		}
		
		try {
			if (p.waitFor() != 0) {
				System.err.println("CMD: " + command);
				System.err.println("RETURNED ERROR: " + out);
			}
		} catch (Exception e) {
			System.err.println(out);
		} finally {
			// Close the InputStream
			bufferedreader.close();
			inread.close();
			buf.close();
			in.close();
		}
		
	}
	
	

}