package edu.isi.bmkeg.sciDP.uima.out;

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
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;

public class SaveLinksBetweenFiguresAndParagraphs extends JCasAnnotator_ImplBase {

	private static Logger logger = Logger.getLogger(SaveLinksBetweenFiguresAndParagraphs.class);

	public final static String PARAM_ANNOT_2_EXTRACT = ConfigurationParameterFactory
			.createConfigurationParameterName(SaveLinksBetweenFiguresAndParagraphs.class, "annot2Extract");
	@ConfigurationParameter(mandatory = false, description = "The section heading *pattern* to extract")
	String annot2Extract;
	private Pattern patt;

	public final static String PARAM_DIR_PATH = ConfigurationParameterFactory
			.createConfigurationParameterName(SaveLinksBetweenFiguresAndParagraphs.class, "outDirPath");
	@ConfigurationParameter(mandatory = true, description = "The path to the output directory.")
	String outDirPath;

	public final static String PARAM_CLAUSE_LEVEL = ConfigurationParameterFactory
			.createConfigurationParameterName(SaveLinksBetweenFiguresAndParagraphs.class, "clauseLevelStr");
	@ConfigurationParameter(mandatory = false, description = "Do we split at the clause level?")
	String clauseLevelStr;
	Boolean clauseLevel = false;

	public final static String PARAM_KEEP_FLOATING_BOXES = ConfigurationParameterFactory
			.createConfigurationParameterName(SaveLinksBetweenFiguresAndParagraphs.class, "keepFloatsStr");
	@ConfigurationParameter(mandatory = false, description = "Should we include floating boxes in the output.")
	String keepFloatsStr = "false";
	Boolean keepFloats = false;

	private File outDir;
	private BioCCollection collection;

	private List<Pattern> figPatt = new ArrayList<Pattern>();
	private List<Pattern> figsPatt = new ArrayList<Pattern>();

	Map<String, Map<String, Integer>> table = new HashMap<String, Map<String, Integer>>();

	public void initialize(UimaContext context) throws ResourceInitializationException {

		super.initialize(context);

		this.outDirPath = (String) context.getConfigParameterValue(PARAM_DIR_PATH);
		this.outDir = new File(this.outDirPath);

		if (!this.outDir.exists())
			this.outDir.mkdirs();

		if (this.clauseLevelStr.toLowerCase().equals("true")) {
			clauseLevel = true;
		}

		if (this.keepFloatsStr.toLowerCase().equals("true")) {
			keepFloats = true;
		}

		this.collection = new BioCCollection();

		if (this.annot2Extract != null)
			this.patt = Pattern.compile(this.annot2Extract);

		//
		// A List of regular expressions to recognize
		// all figure legend codes appearing in text.
		//
		String b = "\\s*[Ff]ig(ure|.){0,1}";
		String e = "\\s*(\\)|\\w{2,}|\\p{Punct})";
		//
		// 0. No alphanumeric codes at all
		this.figPatt.add(Pattern.compile(b + "\\s*(\\d+)\\s*\\p{Punct}*\\s*\\w{2,}"));
		//
		// 1. Delineated by brackets
		this.figPatt.add(Pattern.compile("\\(" + b + "\\s*(\\d+.*?)\\)"));
		//
		// 2. Simple single alphanumeric codes, followed by punctuation.
		this.figPatt.add(Pattern.compile(b + "\\s*(\\d+\\s*[A-Za-z])\\p{Punct}"));

		//
		// 3. Single Alphanumeric codes, followed by words.
		this.figPatt.add(Pattern.compile(b + "\\s*(\\d+\\s*[A-Za-z])\\s+\\w{2,}"));

		//
		// 4. Fig 8, a and b).
		this.figPatt.add(Pattern.compile(b + "\\s*(\\d[\\s\\p{Punct}]*[A-Za-z] and [A-Za-z])" + e));

		//
		// 5. Fig. 3, a-c).
		this.figPatt.add(Pattern.compile(b + "\\s*(\\d[\\s\\p{Punct}]*[A-Z\\-a-z])" + e));

		//
		// 6. Fig. c).
		this.figPatt.add(Pattern.compile(b + "\\s*([\\s\\p{Punct}]*[A-Z\\-a-z])" + e));

		// Multiple Figures in sequence
		b = "\\s*[Ff]ig(ures|s.{0,1}|.){0,1}";
		// 1. Fig. c).
		this.figsPatt.add(Pattern.compile(b + "\\s*([\\s\\p{Punct}]*[A-Z\\-a-z])" + e));
	}

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
		if (uiD.getId().equals("skip"))
			return;

		String id = uiD.getId();

		List<UimaBioCAnnotation> outerAnnotations = JCasUtil.selectCovered(UimaBioCAnnotation.class, uiD);

		File outFile = new File(this.outDir.getPath() + "/" + id + "_fulltext.tsv");
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

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
		
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

		if (this.keepFloats) {
			parags.addAll(floats);
		}
		
		for (UimaBioCAnnotation parag : parags) {

			//
			// Identify exLinks, inLinks or headers
			//
			Map<Integer, Integer> currLvl = new HashMap<Integer, Integer>();

			Set<String> expts = new HashSet<String>();
			for (UimaBioCAnnotation a : JCasUtil.selectCovered(UimaBioCAnnotation.class, parag)) {
				Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
				if (infons.containsKey("refType") && infons.get("refType").equals("fig")) {
					String exptCodes = readExptCodes(jCas, a);
					expts.add(exptCodes);
				}
			}

			if( expts.size() == 0 ) 
				continue;
			
			out.print(uiD.getId());
			out.print("\t");
			out.print(parag.getBegin());
			out.print("\t");
			out.print(parag.getEnd());
			out.print("\t");
			out.print(expts.toString());
			out.print("\t");
			out.print(readTokenizedText(jCas, parag));
			out.print("\n");

		}

	}

	private String readExptCodes(JCas jCas, UimaBioCAnnotation s) {

		String exptCode = s.getCoveredText();
		try {
			
			int offset = 2;
			if (exptCode.toLowerCase().startsWith("fig")) {
				offset = 1;
			}
	
			List<Token> l = JCasUtil.selectCovered(Token.class, s);
			List<Token> f = JCasUtil.selectFollowing(jCas, Token.class, s, 10);
			List<Token> p = JCasUtil.selectPreceding(jCas, Token.class, s, offset);
	
			Token start = p.get(0);
			Token end = f.get(9);
			String figFrag = jCas.getDocumentText().substring(start.getBegin(), end.getEnd());
	
			for (Pattern patt : this.figPatt) {
				Matcher m = patt.matcher(figFrag);
				if (m.find()) {
					// use group 2 since all
					return m.group(2);
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}

		return exptCode;
	}

	private UimaBioCAnnotation readSectionHeading(JCas jCas, UimaBioCAnnotation a) {

		// Looking for section headings
		for (UimaBioCAnnotation a1 : JCasUtil.selectCovering(jCas, UimaBioCAnnotation.class, a.getBegin(),
				a.getEnd())) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a1.getInfons());
			if (infons.containsKey("sectionHeading") && a1.getBegin() == a.getBegin()) {
				return a1;
			}
		}

		return null;

	}

	private int readHeadingLevel(JCas jCas, UimaBioCAnnotation a, int level) throws StackOverflowError {

		// Looking for section headings
		for (UimaBioCAnnotation a1 : JCasUtil.selectCovering(jCas, UimaBioCAnnotation.class, a.getBegin(),
				a.getEnd())) {
			if (a1.equals(a))
				continue;
			Map<String, String> infons = UimaBioCUtils.convertInfons(a1.getInfons());
			if (infons.containsKey("sectionHeading")) {
				level = readHeadingLevel(jCas, a1, level + 1);
				return level;
			}
		}

		return level;

	}

	private String readTokenizedText(JCas jCas, Sentence s) {
		String txt = "";
		for (Token t : JCasUtil.selectCovered(jCas, Token.class, s)) {
			txt += t.getCoveredText() + " ";
		}
		if (txt.length() == 0)
			return txt;
		return txt.substring(0, txt.length() - 1);
	}

	private String readTokenizedText(JCas jCas, UimaBioCAnnotation a) {
		String txt = "";
		for (Token t : JCasUtil.selectCovered(jCas, Token.class, a)) {
			txt += t.getCoveredText() + " ";
		}
		if (txt.length() == 0)
			return txt;
		return txt.substring(0, txt.length() - 1);
	}

}
