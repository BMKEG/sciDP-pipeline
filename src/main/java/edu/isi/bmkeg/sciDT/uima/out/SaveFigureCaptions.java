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

public class SaveFigureCaptions extends JCasAnnotator_ImplBase {

	private static Logger logger = Logger.getLogger(SaveFigureCaptions.class);

	public final static String PARAM_ANNOT_2_EXTRACT = ConfigurationParameterFactory
			.createConfigurationParameterName(SaveFigureCaptions.class, "annot2Extract");
	@ConfigurationParameter(mandatory = false, description = "The section heading *pattern* to extract")
	String annot2Extract;
	private Pattern patt;

	public final static String PARAM_DIR_PATH = ConfigurationParameterFactory
			.createConfigurationParameterName(SaveFigureCaptions.class, "outDirPath");
	@ConfigurationParameter(mandatory = true, description = "The path to the output directory.")
	String outDirPath;

	public final static String PARAM_CLAUSE_LEVEL = ConfigurationParameterFactory
			.createConfigurationParameterName(SaveFigureCaptions.class, "clauseLevelStr");
	@ConfigurationParameter(mandatory = false, description = "Do we split at the clause level?")
	String clauseLevelStr;
	Boolean clauseLevel = false;

	private File outDir;
	private BioCCollection collection;

	private Pattern figPatt;
	private List<Pattern> subFigPatt = new ArrayList<Pattern>();

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

		this.collection = new BioCCollection();

		if (this.annot2Extract != null)
			this.patt = Pattern.compile(this.annot2Extract);

		//
		// A List of regular expressions to recognize
		// all figure legend codes appearing in text.
		//
		this.figPatt = Pattern.compile("\\s*[Ff]ig(ure|.){0,1}\\s+(\\d+)");

		//this.subFigPatt.add(Pattern.compile("\\s*(\\d+)\\s*\\p{Punct}*\\s*\\w{2,}"));
		
		//
		// 1. Delineated by brackets
		this.subFigPatt.add(Pattern.compile("^\\s*\\(\\s*([A-Za-z])\\s*\\)"));
		//
		// 2. Simple single alphanumeric codes, followed by punctuation.
		//this.subFigPatt.add(Pattern.compile("^\\s*(\\s*[A-Za-z]\\s*)\\p{Punct}"));

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
		for (UimaBioCAnnotation a : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, start, end)) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
			if (infons.containsKey("position") && infons.get("position").equals("float")) {
				floats.add(a);
			}
		}
		
		for (UimaBioCAnnotation fc : floats) {

			String fig = "";
			String subFig = "-";
			String oldSubFig = "-";
			Matcher m = figPatt.matcher(fc.getCoveredText());
			if (m.find()) {
				fig = m.group(2);
			}
			
			if( fig.equals("") )
				continue;
			
			Map<String, String> infons = UimaBioCUtils.convertInfons(fc.getInfons());
			if (infons.get("type").equals("formatting") && infons.get("value").equals("fig")) {
			
				String txt = "";
				for (Sentence s : JCasUtil.selectCovered(Sentence.class, fc)) {
					
					String sText = this.readTokenizedText(jCas, s);

					Matcher m2 = figPatt.matcher(sText);
					if(m2.find()) {
						txt = sText + ". ";
						continue;
					}
					
					String subFigCode = readSubFigCodes(jCas, s);
					if( !subFigCode.equals("-") )
						subFig = subFigCode;

					if( !subFig.equals(oldSubFig) ) {
						if( !oldSubFig.equals("-") ) {
							out.print(uiD.getId());
							out.print("\t");
							out.print(fig);
							out.print("\t");
							out.print(oldSubFig);
							out.print("\t");
							out.print(txt);
							out.print("\t");
							out.print("\n");				
						}
						
						txt = "";
						oldSubFig = subFig;
					
					}

					if( sText.endsWith(".") )
						txt += " " + sText;
					else 
						txt += ". " + sText;
					
				}
				
				if( !subFig.equals("-") ) {
					out.print(uiD.getId());
					out.print("\t");
					out.print(fig);
					out.print("\t");
					out.print(subFig);
					out.print("\t");
					out.print(txt);
					out.print("\t");
					out.print("\n");
				}
				
			}

		}

	}
	
	private String readSubFigCodes(JCas jCas, Sentence s) {

		String exptCode = "-";
		try {
			
			String figFrag = s.getCoveredText();
	
			for (Pattern patt : this.subFigPatt) {
				Matcher m = patt.matcher(figFrag);
				if (m.find()) {
					// use group 2 since all
					return m.group(1);
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}

		return exptCode;
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

}
