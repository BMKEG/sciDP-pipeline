package edu.isi.bmkeg.sciDT.uima.out;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
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
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.token.type.Sentence;
import org.cleartk.token.type.Token;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.ConfigurationParameterFactory;
import org.uimafit.util.JCasUtil;

import bioc.BioCCollection;
import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import bioc.type.UimaBioCLocation;
import bioc.type.UimaBioCPassage;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;
import edu.isi.bmkeg.uimaBioC.rubicon.MatchReachAndNxmlText;

public class SaveAsSciDP extends JCasAnnotator_ImplBase {

	private static Logger logger = Logger.getLogger(SaveAsSciDP.class);

	public final static String PARAM_DIR_PATH = ConfigurationParameterFactory
			.createConfigurationParameterName(SaveAsSciDP.class, "outDirPath");
	@ConfigurationParameter(mandatory = true, description = "The path to the output directory.")
	String outDirPath;

	private File outDir;
	private BioCCollection collection;


	Map<String, Map<String, Integer>> table = new HashMap<String, Map<String, Integer>>();

	public void initialize(UimaContext context) throws ResourceInitializationException {

		super.initialize(context);

		this.outDirPath = (String) context.getConfigParameterValue(PARAM_DIR_PATH);
		this.outDir = new File(this.outDirPath);

		if (!this.outDir.exists())
			this.outDir.mkdirs();

		this.collection = new BioCCollection();

	}

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
		if (uiD.getId().equals("skip"))
			return;

		String id = uiD.getId();

		File outFile = new File(this.outDir.getPath() + "/" + id + "_scidp.txt");
		PrintWriter out;
		try {
			out = new PrintWriter(new BufferedWriter(new FileWriter(outFile, true)));
		} catch (IOException e) {
			throw (new AnalysisEngineProcessException(e));
		}

		this.dumpSectionToFile(jCas, out, uiD.getBegin(), uiD.getEnd());

		out.close();

	}

	private void dumpSectionToFile(JCas jCas, PrintWriter out, int start, int end)
			throws AnalysisEngineProcessException {

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
		List<Sentence> floatingSentences = new ArrayList<Sentence>();
		String oldPCode = "-";
		SENTENCE_LOOP: for (Sentence s : sentences) {

			for (UimaBioCAnnotation f : floats) {
				if (s.getBegin() >= f.getBegin() && s.getEnd() <= f.getEnd()) {
					floatingSentences.add(s);
					continue SENTENCE_LOOP;
				}
			}

			List<UimaBioCAnnotation> clauseList = new ArrayList<UimaBioCAnnotation>();
			for (UimaBioCAnnotation a : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, s)) {
				Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
				if (infons.get("type").equals("rubicon") && infons.get("value").equals("clause"))
					clauseList.add(a);
			}
			
			for (UimaBioCAnnotation clause : clauseList) {

				Map<String, String> infons = UimaBioCUtils.convertInfons(clause.getInfons());
				String pCode = infons.get("scidp-paragraph-number");
				
				if( !pCode.equals(oldPCode) 
						&& !pCode.startsWith("article-title") 
						&& !oldPCode.startsWith("article-title") 
						&& !(pCode.startsWith("title") && oldPCode.startsWith("title") ) 
						&& !(pCode.startsWith("title") && oldPCode.startsWith("label") ) 
						&& !(pCode.startsWith("p") && oldPCode.startsWith("label") ) 
						&& !(pCode.startsWith("p") && oldPCode.startsWith("title") ) ) {
					out.print( "\n" );					
				}
				
				out.print(UimaBioCUtils.readTokenizedText(jCas, clause));
				out.print("\n");
				oldPCode = pCode;
			}

		}
		
		SENTENCE_LOOP: for (Sentence s : floatingSentences) {

			List<UimaBioCAnnotation> clauseList = new ArrayList<UimaBioCAnnotation>();
			for (UimaBioCAnnotation a : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, s)) {
				Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
				if (infons.get("type").equals("rubicon") && infons.get("value").equals("clause"))
					clauseList.add(a);
			}
			
			for (UimaBioCAnnotation clause : clauseList) {
				
				Map<String, String> infons = UimaBioCUtils.convertInfons(clause.getInfons());
				String pCode = infons.get("scidp-paragraph-number");
				
				if( !pCode.equals(oldPCode) 
						&& !pCode.startsWith("article-title") 
						&& !oldPCode.startsWith("article-title") 
						&& !(pCode.startsWith("title") && oldPCode.startsWith("title") ) 
						&& !(pCode.startsWith("title") && oldPCode.startsWith("label") ) 
						&& !(pCode.startsWith("p") && oldPCode.startsWith("label") ) 
						&& !(pCode.startsWith("p") && oldPCode.startsWith("title") ) ) {
					out.print( "\n" );					
				}
				out.print(UimaBioCUtils.readTokenizedText(jCas, clause));
				out.print("\n");
				oldPCode = pCode;
			}

		}
		

	}


	
	
}
