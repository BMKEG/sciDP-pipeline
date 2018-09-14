package edu.isi.bmkeg.sciDT.uima.ae;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;

import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.ConfigurationParameterFactory;
import org.uimafit.util.JCasUtil;

import com.google.gson.Gson;

import bioc.BioCAnnotation;
import bioc.BioCDocument;
import bioc.BioCLocation;
import bioc.BioCPassage;
import bioc.io.BioCDocumentReader;
import bioc.io.BioCFactory;
import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import bioc.type.UimaBioCPassage;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;
import edu.isi.bmkeg.uimaBioC.uima.readers.BioCCollectionReader;

public class MergeBioCData extends JCasAnnotator_ImplBase {

	public final static String PARAM_INPUT_DIRECTORY = ConfigurationParameterFactory
			.createConfigurationParameterName(MergeBioCData.class, "inDirPath");
	@ConfigurationParameter(mandatory = true, description = "Directory for the extra BioC to be merged.")
	String inDirPath;
	File inDir;
	
	public static String XML = "xml";
	public static String JSON = "json";
	public final static String PARAM_FORMAT = ConfigurationParameterFactory
			.createConfigurationParameterName(MergeBioCData.class,
					"inFileFormat");
	@ConfigurationParameter(mandatory = true, description = "The format of the BioC input files.")
	String inFileFormat;
	

	Pattern alignmentPattern = Pattern.compile("^(.{0,3}_+)");

	private static Logger logger = Logger.getLogger(MergeBioCData.class);

	public void initialize(UimaContext context) throws ResourceInitializationException {

		super.initialize(context);

		this.inDir = new File(this.inDirPath);

	}

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		try {

			UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
			if (uiD.getId().equals("skip"))
				return;

			UimaBioCPassage docP = UimaBioCUtils.readDocument(jCas);

			logger.info("Merging BioC files from " + uiD.getId());

			Map<String, String> topInfons = UimaBioCUtils.convertInfons(uiD.getInfons());
			String pmcDocId = "PMC" + topInfons.get("pmc");

			File baseFile = new File(this.inDir.getPath() + "/" + uiD.getId() + ".json");

			if (!baseFile.exists()) {
				logger.warn("SciDP input: " + baseFile.getPath() + " does not exist");
				return;
			}

			BioCDocument bioD = readBioCFile(baseFile);
			for( BioCPassage bioP : bioD.getPassages() ) {
				FIND_LOOP: for( BioCAnnotation bioA : bioP.getAnnotations() ) {
					BioCLocation loc = bioA.getLocations().get(0);
					int start = loc.getOffset();
					int end = loc.getOffset() + loc.getLength();
					String s1 = UimaBioCUtils.stringify(bioA);
					for (UimaBioCAnnotation a : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, start, end)) {
						String s2 = UimaBioCUtils.stringify(a);
						if( s1.equals(s2) ) {
							continue FIND_LOOP;
						}
					}
					// If we get here, then we haven't found bioA in the document and we can add it.
					UimaBioCAnnotation aa = UimaBioCUtils.convertBioCAnnotation(bioA, jCas);
					aa.addToIndexes();
				}
			}
					
			logger.info("Loading SciDP Annotations from " + uiD.getId() + " - COMPLETED");

		} catch (Exception e) {

			throw new AnalysisEngineProcessException(e);

		}

	}
	
	private BioCDocument readBioCFile(File bioCFile)
			throws AnalysisEngineProcessException, FileNotFoundException, XMLStreamException, IOException {
		
		
		BioCDocument bioD = null;
		
		if (inFileFormat.equals(XML)) {
			
			BioCDocumentReader reader = BioCFactory.newFactory(
					BioCFactory.STANDARD).createBioCDocumentReader(
							new FileReader(bioCFile));

			bioD = reader.readDocument();

			reader.close();

		} else if (inFileFormat.equals(JSON)) {

			Gson gson = new Gson();
			bioD = gson.fromJson(new FileReader(bioCFile), BioCDocument.class);
			
		} else {
			
			throw new AnalysisEngineProcessException(
					new Exception("Please write to an *.xml or a *.json file")
					);
		
		}
	
		return bioD;
	
	}

}
