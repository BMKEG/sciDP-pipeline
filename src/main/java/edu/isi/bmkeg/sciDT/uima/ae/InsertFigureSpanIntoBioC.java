package edu.isi.bmkeg.sciDT.uima.ae;

import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import bioc.type.UimaBioCPassage;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.token.type.Sentence;
import org.simmetrics.StringMetric;
import org.simmetrics.StringMetricBuilder;
import org.simmetrics.metrics.Levenshtein;
import org.simmetrics.simplifiers.NonDiacriticSimplifier;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.ConfigurationParameterFactory;
import org.uimafit.util.JCasUtil;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

public class InsertFigureSpanIntoBioC extends JCasAnnotator_ImplBase {

	public final static String PARAM_INPUT_DIRECTORY = ConfigurationParameterFactory
			.createConfigurationParameterName(InsertFigureSpanIntoBioC.class, "inDirPath");
	@ConfigurationParameter(mandatory = true, description = "Directory for the SciDP Data.")
	String inDirPath;
	File inDir;
	
	Pattern alignmentPattern = Pattern.compile("^(.{0,3}_+)");

	private static Logger logger = Logger.getLogger(InsertFigureSpanIntoBioC.class);

	private StringMetric levenshteinSimilarityMetric;
	
	public void initialize(UimaContext context) throws ResourceInitializationException {

		super.initialize(context);

		this.inDir = new File(this.inDirPath);
		
		this.levenshteinSimilarityMetric = new StringMetricBuilder().with(new Levenshtein())
				.simplify(new NonDiacriticSimplifier()).build();
		
	}

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		try {

			UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
			if (uiD.getId().equals("skip"))
				return;

			UimaBioCPassage docP = UimaBioCUtils.readDocument(jCas);

			logger.info("Loading SciDT Annotations from " + uiD.getId());

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
				if (f.getName().startsWith(pmcDocId+".") || 
						f.getName().startsWith(uiD.getId()+"_") || 
						f.getName().startsWith(uiD.getId()+".")) {
					tsvFile = f;
					break;
				}
			}
			
			if (tsvFile == null || !tsvFile.exists()) {
				logger.warn("TSV input for " + uiD.getId() + " does not exist");	
				uiD.setId("skip");
				return;
			}

			List<List<String>> table = readLinesFromFile(tsvFile);
			
			List<UimaBioCAnnotation> outerAnnotations = JCasUtil.selectCovered(UimaBioCAnnotation.class, uiD);
			
			this.dumpSectionToFile(jCas, table, uiD.getBegin(), uiD.getEnd());

			logger.info("Loading SciDT Annotations from " + uiD.getId() + " - COMPLETED");

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
		
		int discourseTypeColumn = -1, textColumn = -1, figAssignment = -1, offsetBeginCol = -1, offsetEndCol = -1;
		for(int i=0; i<table.get(0).size(); i++) {
			String colHeading = table.get(0).get(i);
			if(colHeading.equals("Discourse Type") ) 
				discourseTypeColumn = i;
			else if(colHeading.equals("Clause Text"))
				textColumn = i;
			else if(colHeading.equals("fig_spans"))
				figAssignment = i;
			else if(colHeading.equals("Offset_Begin"))
				offsetBeginCol = i;
			else if(colHeading.equals("Offset_End"))
				offsetEndCol = i;
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
				
				if( table.get(counter).size()==2)
					continue;
				
				String text = table.get(counter).get(textColumn);
				if( text.startsWith("\"") ) 
					text = text.substring(1, text.length());
				if( text.endsWith("\"") ) 
					text = text.substring(0, text.length()-1);
				text = text.replaceAll("\"", "");
				String t1 = UimaBioCUtils.readTokenizedText(jCas, clause);
				
				t1 = t1.replaceAll("\"", "");
				
				if( !text.equals(t1)) {
					float sim1 = levenshteinSimilarityMetric.compare(
							text.replaceAll("\\s+", ""), t1.replaceAll("\\s+", ""));
					if( sim1 < 0.95 ){
						
						logger.warn("Mismatch between text in BioC and TSV\n" +
								"    Bioc: " + UimaBioCUtils.readTokenizedText(jCas, clause) + "\n" +
								"    TSV: " + text + "\n");
						//throw new Exception("Mismatch between text in BioC and TSV\n" +
						//		"    Bioc: " + UimaBioCUtils.readTokenizedText(jCas, clause) + "\n" +
						//		"    TSV: " + text + "\n");
					}
				}
				
				Map<String, String> inf = UimaBioCUtils.convertInfons(clause.getInfons());
				if( table.get(counter).size() > discourseTypeColumn) {
					String fa = table.get(counter).get(discourseTypeColumn);
					if( fa.length() != 0 )
						inf.put("scidp-discourse-type", fa);
				}
				
				if( figAssignment != -1 && table.get(counter).size() > figAssignment) {
					String fa = table.get(counter).get(figAssignment);
					if( fa.length() != 0 )
						inf.put("scidp-fig-assignment", fa);
				}
				
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
