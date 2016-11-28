package edu.isi.bmkeg.sciDT.uima.ae;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.token.type.Sentence;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.ConfigurationParameterFactory;
import org.uimafit.util.JCasUtil;

import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import bioc.type.UimaBioCPassage;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;

public class InsertTsvBackIntoBioC extends JCasAnnotator_ImplBase {

	public final static String PARAM_INPUT_DIRECTORY = ConfigurationParameterFactory
			.createConfigurationParameterName(InsertTsvBackIntoBioC.class, "inDirPath");
	@ConfigurationParameter(mandatory = true, description = "Directory for the SciDP Data.")
	String inDirPath;
	File inDir;
	
	Pattern alignmentPattern = Pattern.compile("^(.{0,3}_+)");

	private static Logger logger = Logger.getLogger(InsertTsvBackIntoBioC.class);

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

			logger.info("Loading SciDP Annotations from " + uiD.getId());

			Map<String, String> topInfons = UimaBioCUtils.convertInfons(uiD.getInfons());
			String pmcDocId = "PMC";
			if( topInfons.containsKey("pmc") ) {
				pmcDocId += topInfons.get("pmc");
			} else if( topInfons.containsKey("pmcid") ) {
				pmcDocId = topInfons.get("pmcid");
			}

			String[] fileTypes = { "tsv" };
			Collection<File> files = (Collection<File>) FileUtils.listFiles(this.inDir, fileTypes, true);
			File tsvFile = null;
			for (File f : files) {
				if (f.getName().startsWith(pmcDocId + "." ) ) {
					tsvFile = f;
					break;
				}
			}
			
			if (tsvFile == null || !tsvFile.exists()) {
				throw new Exception("TSV input: " + tsvFile.getPath() + " does not exist");	
			}

			List<List<String>> table = readLinesFromFile(tsvFile);
			
			List<UimaBioCAnnotation> outerAnnotations = JCasUtil.selectCovered(UimaBioCAnnotation.class, uiD);
			
			this.dumpSectionToFile(jCas, table, uiD.getBegin(), uiD.getEnd());

			logger.info("Loading SciDP Annotations from " + uiD.getId() + " - COMPLETED");

		} catch (Exception e) {

			throw new AnalysisEngineProcessException(e);

		}

	}
	
	private List<List<String>> readLinesFromFile(File baseFile) throws FileNotFoundException, IOException {
		
		FileReader fileReader = new FileReader(baseFile);
		BufferedReader bufferedReader = new BufferedReader(fileReader);
		List<List<String>> lines = new ArrayList<List<String>>();
		String line = null;
		while ((line = bufferedReader.readLine()) != null) {
			String[] fieldArray = line.split("\\t");
			List<String> fields = new ArrayList<String>();
			for( String field : fieldArray)
				fields.add(field);
			lines.add(fields);
		}
		bufferedReader.close();
		
		return lines;
	}

	private void dumpSectionToFile(JCas jCas, List<List<String>> table, int start, int end)
			throws Exception {


		
		List<UimaBioCAnnotation> floats = new ArrayList<UimaBioCAnnotation>();
		List<UimaBioCAnnotation> parags = new ArrayList<UimaBioCAnnotation>();
		for (UimaBioCAnnotation a : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, start, end)) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
			if (infons.containsKey("position") && infons.get("position").equals("float")) {
				floats.add(a);
			} else if (infons.containsKey("value")
					&& (infons.get("value").equals("p") 
							|| infons.get("value").equals("title"))) {
				parags.add(a);
			}

		}
		
		int discourseTypeColumn = -1, textColumn = -1;
		for(int i=0; i<table.get(0).size(); i++) {
			String colHeading = table.get(0).get(i);
			if(colHeading.equals("Discourse Type") ) 
				discourseTypeColumn = i;
			else if(colHeading.equals("Clause Text"))
				textColumn = i;
		}
		
		if( discourseTypeColumn == -1 )
			throw new Exception("Discourse type column not set");
		
		int counter = 1;
		
		List<Sentence> sentences = JCasUtil.selectCovered(jCas, Sentence.class, start, end);
		String oldPCode = "";
		SENTENCE_LOOP: for (Sentence s : sentences) {

			// Put the floats at the end.
			for (UimaBioCAnnotation f : floats) {
				if (s.getBegin() >= f.getBegin() && s.getEnd() <= f.getEnd()) {
					continue SENTENCE_LOOP;
				}
			}

			//
			// Identify exLinks, inLinks or headers
			//
			Map<Integer, Integer> currLvl = new HashMap<Integer, Integer>();

			//
			// Look for paragraphs
			//
			String pCode = "-";
			for (int i = 0; i < parags.size(); i++) {
				UimaBioCAnnotation p = parags.get(i);
				if (s.getBegin() >= p.getBegin() && s.getEnd() <= p.getEnd()) {
					UimaBioCUtils.convertInfons(p.getInfons()).get("value");
					pCode = UimaBioCUtils.convertInfons(p.getInfons()).get("value") + i;
					break;
				}
			}

			List<UimaBioCAnnotation> clauseList = new ArrayList<UimaBioCAnnotation>();
			for (UimaBioCAnnotation a : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, s)) {
				Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
				if (infons.get("type").equals("rubicon") && infons.get("value").equals("clause"))
					clauseList.add(a);
			}
			
			if(clauseList.size() == 0) {
				UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
				logger.warn("No Clauses Found in "+uiD.getId()+"("+s.getBegin() +"-"+s.getEnd()+"): " + s.getCoveredText());
				
				Map<String, String> infons = new HashMap<String, String>();
				infons.put("type", "rubicon");
				infons.put("value", "clause");
				UimaBioCAnnotation newCl = UimaBioCUtils.createNewAnnotation(jCas, s.getBegin(), s.getEnd(), infons); 
				clauseList.add(newCl);

			}

			for (UimaBioCAnnotation clause : clauseList) {
				
				String text = table.get(counter).get(textColumn);
				if( text.startsWith("\"") ) 
					text = text.substring(1, text.length());
				if( text.endsWith("\"") ) 
					text = text.substring(0, text.length()-1);
				
				if( !text.equals(UimaBioCUtils.readTokenizedText(jCas, clause))) {
					throw new Exception("Mismatch between text in BioC and TSV\n" +
							"    Bioc: " + UimaBioCUtils.readTokenizedText(jCas, clause) + "\n" +
							"    TSV: " + text + "\n");
				}
				
				Map<String, String> inf = UimaBioCUtils.convertInfons(clause.getInfons());
				inf.put("scidp-discourse-type", table.get(counter).get(discourseTypeColumn));
				clause.setInfons(UimaBioCUtils.convertInfons(inf, jCas));
				
				counter++;
				if(counter >= table.size()) 
					return;
				
			}

		}

	}
	

	private int countChars(String s, String c) {

		String mod = s.replaceAll(c, "");
		return (s.length() - mod.length());

	}

}
