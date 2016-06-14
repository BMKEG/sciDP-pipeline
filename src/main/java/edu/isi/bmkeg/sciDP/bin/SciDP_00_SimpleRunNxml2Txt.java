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
 * This script runs through serialized JSON files from the model and converts
 * them to VPDMf KEfED models, including the data.
 * 
 * @author Gully
 * 
 */
public class SciDP_00_SimpleRunNxml2Txt {

	public static class Options {

		@Option(name = "-inDir", usage = "Input Directory", required = true, metaVar = "INPUT")
		public File inDir;

		@Option(name = "-outDir", usage = "Output", required = true, metaVar = "OUTPUT")
		public File outDir;

		@Option(name = "-execPath", usage = "Path to the nxml2text executable", required = true, metaVar = "PATH")
		public File execPath;

		@Option(name = "-suffix", usage = "Altered suffix of *.nxml files", required = false, metaVar = "NXML SUFFIX")
		public String suffix = "nxml";
		
	}

	private static Logger logger = Logger
			.getLogger(SciDP_00_SimpleRunNxml2Txt.class);

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

		if( !options.outDir.exists() )
			options.outDir.mkdirs();

		Pattern pattern = Pattern.compile("\\.(.*)$");
		Matcher matcher = pattern.matcher(options.suffix);
		String fileEx = options.suffix;
		if( matcher.find() )
			fileEx = matcher.group(1);
		
		String[] fileTypes = {fileEx};
		
		@SuppressWarnings("unchecked")
		Iterator<File> it = FileUtils.iterateFiles(options.inDir, fileTypes, true);
		
		while( it.hasNext() ) {
			File f = it.next();
			
			if( !f.getName().endsWith(options.suffix) )
				continue;
				
			String newPath = f.getPath().replaceAll(
					options.inDir.getPath(),
					options.outDir.getPath());
			String s = "." + options.suffix + "$";
			File txtFile = new File(newPath.replaceAll(s, ".txt"));
			File annFile = new File(newPath.replaceAll(s, ".so"));
			File logFile = new File(newPath.replaceAll(s, "_nxml2txt.log"));
			txtFile.getParentFile().mkdirs();

			String command = "python " + options.execPath.getPath() + " " + f.getPath() 
					+ " " + txtFile.getPath()
					+ " " + annFile.getPath() + "";

			if( txtFile.getPath().contains(" ") )
				command = "python " + options.execPath.getPath() + " \"" + f.getPath() 
				+ "\" \"" + txtFile.getPath()
				+ "\" \"" + annFile.getPath() + "\"";

			ProcessBuilder pb = new ProcessBuilder(command.split(" "));
			Map<String,String> env = pb.environment();
			env.put("PYTHONPATH", "/usr/local/lib/python2.7/site-packages");
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

}