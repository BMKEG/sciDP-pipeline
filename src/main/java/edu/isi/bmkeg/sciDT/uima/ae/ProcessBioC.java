package edu.isi.bmkeg.sciDT.uima.ae;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
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

/**
 * Higher level class to process BioC text in a standard way using multiple methods
 * @author Gully
 *
 */
public abstract class ProcessBioC extends JCasAnnotator_ImplBase {

	public final static String PARAM_INPUT_DIRECTORY = ConfigurationParameterFactory
			.createConfigurationParameterName(ProcessBioC.class, "inDirPath");
	@ConfigurationParameter(mandatory = true, description = "Directory for the SciDP Data.")
	String inDirPath;
	File inDir;

	public final static String PARAM_ANNOT_2_EXTRACT = ConfigurationParameterFactory
			.createConfigurationParameterName(ProcessBioC.class, "annot2Extract");
	@ConfigurationParameter(mandatory = false, description = "The section heading *pattern* to extract")
	String annot2Extract;
	Pattern patt;

	public final static String PARAM_KEEP_FLOATING_BOXES = ConfigurationParameterFactory
			.createConfigurationParameterName(ProcessBioC.class, "keepFloatsStr");
	@ConfigurationParameter(mandatory = false, description = "Should we include floating boxes in the output.")
	String keepFloatsStr;
	Boolean keepFloats = false;
	
	Pattern alignmentPattern = Pattern.compile("^(.{0,3}_+)");

	static Logger logger = Logger.getLogger(ProcessBioC.class);

	public void initialize(UimaContext context) throws ResourceInitializationException {

		super.initialize(context);

		this.inDir = new File(this.inDirPath);

		if (this.keepFloatsStr.toLowerCase().equals("true")) {
			keepFloats = true;
		}

		if (this.annot2Extract != null)
			this.patt = Pattern.compile(this.annot2Extract);
		
	}

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		try {

			UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
			if (uiD.getId().equals("skip"))
				return;

			UimaBioCPassage docP = UimaBioCUtils.readDocument(jCas);

			logger.info("Loading SciDP Annotations from " + uiD.getId());
			
			List<UimaBioCAnnotation> outerAnnotations = JCasUtil.selectCovered(UimaBioCAnnotation.class, uiD);
			
			if (this.annot2Extract != null) {

				Set<UimaBioCAnnotation> selectedAnnotations = new HashSet<UimaBioCAnnotation>();
				for (UimaBioCAnnotation uiA1 : outerAnnotations) {

					Map<String, String> inf = UimaBioCUtils.convertInfons(uiA1.getInfons());
					if (!inf.containsKey("type"))
						continue;

					if (!(inf.get("type").equals("formatting") && inf.get("value").equals("sec"))) {
						continue;
					}

					if (this.patt != null) {
						Matcher match = this.patt.matcher(inf.get("sectionHeading"));
						if (!match.find()) {
							continue;
						}
					}

					selectedAnnotations.add(uiA1);

				}

				int maxL = 0;
				UimaBioCAnnotation bestA = null;
				for (UimaBioCAnnotation uiA : selectedAnnotations) {
					int l = uiA.getEnd() - uiA.getBegin();
					if (l > maxL) {
						bestA = uiA;
						maxL = l;
					}
				}

				if (bestA != null) {

					this.dumpSectionToFile(jCas, bestA.getBegin(), bestA.getEnd());

				} else {

					logger.error("Couldn't find a section heading corresponding to " + this.annot2Extract + " in "
							+ uiD.getId());

				}

			} else {

				this.dumpSectionToFile(jCas, uiD.getBegin(), uiD.getEnd());

			}
			
			logger.info("Loading SciDP Annotations from " + uiD.getId() + " - COMPLETED");

		} catch (Exception e) {

			throw new AnalysisEngineProcessException(e);

		}

	}
	
	List<UimaBioCAnnotation> buildClauseList(JCas jCas, int start, int end) throws Exception {
				
		List<UimaBioCAnnotation> clauseList = new ArrayList<UimaBioCAnnotation>();

		List<UimaBioCAnnotation> floats = new ArrayList<UimaBioCAnnotation>();
		List<UimaBioCAnnotation> parags = new ArrayList<UimaBioCAnnotation>();
		for (UimaBioCAnnotation a : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, start, end)) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
			if (infons.containsKey("position") && infons.get("position").equals("float")) {
				floats.add(a);
			} else if (infons.containsKey("value")
					&& (infons.get("value").equals("p") || infons.get("value").equals("title"))) {
				parags.add(a);
			}

		}

		List<Sentence> sentences = JCasUtil.selectCovered(jCas, Sentence.class, start, end);
		String oldPCode = "";
		SENTENCE_LOOP: for (Sentence s : sentences) {

			if (!this.keepFloats) {
				for (UimaBioCAnnotation f : floats) {
					if (s.getBegin() >= f.getBegin() && s.getEnd() <= f.getEnd()) {
						continue SENTENCE_LOOP;
					}
				}
			}

			//
			// Identify exLinks, inLinks or headers
			//
			Map<Integer, Integer> currLvl = new HashMap<Integer, Integer>();

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
		}
		
		return clauseList;

	}

	String readPCode(JCas jCas, int begin, int end) {

		List<UimaBioCAnnotation> parags = new ArrayList<UimaBioCAnnotation>();
		for (UimaBioCAnnotation a : JCasUtil.selectCovering(jCas, UimaBioCAnnotation.class, begin, end)) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
			if (infons.containsKey("value")
					&& (infons.get("value").equals("p") || infons.get("value").equals("title"))) {
				parags.add(a);
			}
		}
		
		String pCode = "-";
		for (int i = 0; i < parags.size(); i++) {
			UimaBioCAnnotation p = parags.get(i);
			if (begin >= p.getBegin() && end <= p.getEnd()) {
				UimaBioCUtils.convertInfons(p.getInfons()).get("value");
				pCode = UimaBioCUtils.convertInfons(p.getInfons()).get("value") + i;
				break;
			}
		}
		
		return pCode;
		
	}

	void dumpSectionToFile(JCas jCas, int start, int end)
			throws Exception {}

}
