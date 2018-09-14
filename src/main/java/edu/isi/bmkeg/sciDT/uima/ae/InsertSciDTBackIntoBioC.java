package edu.isi.bmkeg.sciDT.uima.ae;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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

public class InsertSciDTBackIntoBioC extends JCasAnnotator_ImplBase {

	public final static String PARAM_INPUT_DIRECTORY = ConfigurationParameterFactory
			.createConfigurationParameterName(InsertSciDTBackIntoBioC.class, "inDirPath");
	@ConfigurationParameter(mandatory = true, description = "Directory for the SciDP Data.")
	String inDirPath;
	File inDir;

	Pattern alignmentPattern = Pattern.compile("^(.{0,3}_+)");

	private static Logger logger = Logger.getLogger(InsertSciDTBackIntoBioC.class);

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
			String pmcDocId = "PMC" + topInfons.get("pmc");

			File baseFile = new File(this.inDir.getPath() + "/" + uiD.getId() + "_scidp.txt");
			File outputFile = new File(
					this.inDir.getPath() + "/" + uiD.getId() + "_scidp_att=True_cont=word_bid=False.out");

			if (!baseFile.exists()) {
				logger.warn("SciDP input: " + baseFile.getPath() + " does not exist");
				return;
			}

			if (!outputFile.exists()) {
				logger.warn("SciDP output: " + outputFile.getPath() + " does not exist");
				return;
			}

			List<String> baseLines = readLinesFromFile(baseFile);
			List<String> outLines = readLinesFromFile(outputFile);

			if (baseLines.size() != outLines.size()) {
				logger.warn("SciDP inputs and outputs don't match for " + uiD.getId());
			}

			try {
				this.dumpSectionToFile(jCas, baseLines, outLines, uiD);
			} catch( Exception e ) {
				logger.warn("Loading SciDP Annotations from " + uiD.getId() + " - FAILED");
				uiD.setId("skip");
			}

			logger.info("Loading SciDP Annotations from " + uiD.getId() + " - COMPLETED");

		} catch (Exception e) {

			throw new AnalysisEngineProcessException(e);

		}

	}

	private List<String> readLinesFromFile(File baseFile) throws FileNotFoundException, IOException {

		FileReader fileReader = new FileReader(baseFile);
		BufferedReader bufferedReader = new BufferedReader(fileReader);
		List<String> lines = new ArrayList<String>();
		String line = null;
		while ((line = bufferedReader.readLine()) != null) {
			if (line.length() > 0)
				lines.add(line);
		}
		bufferedReader.close();

		return lines;
	}

	private void dumpSectionToFile(JCas jCas, List<String> baseLines, List<String> outLines, UimaBioCDocument uiD)
			throws Exception {

		int counter = 0;

		List<UimaBioCAnnotation> floats = new ArrayList<UimaBioCAnnotation>();
		List<UimaBioCAnnotation> parags = new ArrayList<UimaBioCAnnotation>();
		for (UimaBioCAnnotation a : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, uiD)) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
			if (infons.containsKey("position") && infons.get("position").equals("float")) {
				floats.add(a);
			} else if (infons.containsKey("value")
					&& (infons.get("value").equals("p") || infons.get("value").equals("title"))) {
				parags.add(a);
			}

		}

		List<Sentence> sentences = JCasUtil.selectCovered(jCas, Sentence.class, uiD);
		List<Sentence> floatingSentences = new ArrayList<Sentence>();
		String oldPCode = "";
		SENTENCE_LOOP: for (Sentence s : sentences) {

			for (UimaBioCAnnotation f : floats) {
				if (s.getBegin() >= f.getBegin() && s.getEnd() <= f.getEnd()) {
					floatingSentences.add(s);
					continue SENTENCE_LOOP;
				}
			}
		
			counter = this.updateSentence(jCas, baseLines, outLines, s, counter);

		}
		
		SENTENCE_LOOP: for (Sentence s : floatingSentences) {
			
			counter = this.updateSentence(jCas, baseLines, outLines, s, counter);
			
		}

	}

	private int updateSentence(JCas jCas,  List<String> baseLines, List<String> outLines, Sentence s, int counter) throws Exception {
		
		List<UimaBioCAnnotation> clauseList = new ArrayList<UimaBioCAnnotation>();
		for (UimaBioCAnnotation a : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, s)) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
			if (infons.get("type").equals("rubicon") && infons.get("value").equals("clause"))
				clauseList.add(a);
		}

		for (UimaBioCAnnotation clause : clauseList) {

			if (!baseLines.get(counter).equals((UimaBioCUtils.readTokenizedText(jCas, clause)))) {
				throw new Exception("Mismatch between bioc and sciDp codes \n" + "    " + baseLines.get(counter)
						+ "\n" + "    " + outLines.get(counter) + "\n");
			}

			Map<String, String> inf = UimaBioCUtils.convertInfons(clause.getInfons());
			inf.put("scidp-discourse-type", outLines.get(counter));
			clause.setInfons(UimaBioCUtils.convertInfons(inf, jCas));

			counter++;
			if (counter >= outLines.size())
				return counter;

		}
		
		return counter;
				
	}

	private int countChars(String s, String c) {

		String mod = s.replaceAll(c, "");
		return (s.length() - mod.length());

	}

}
