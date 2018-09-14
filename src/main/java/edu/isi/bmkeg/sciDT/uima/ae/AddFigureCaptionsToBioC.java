package edu.isi.bmkeg.sciDT.uima.ae;

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

public class AddFigureCaptionsToBioC extends JCasAnnotator_ImplBase {

	private static Logger logger = Logger.getLogger(AddFigureCaptionsToBioC.class);

	public final static String PARAM_CLAUSE_LEVEL = ConfigurationParameterFactory
			.createConfigurationParameterName(AddFigureCaptionsToBioC.class, "clauseLevelStr");
	@ConfigurationParameter(mandatory = false, description = "Do we split at the clause level?")
	String clauseLevelStr;
	Boolean clauseLevel = false;

	private BioCCollection collection;

	private Pattern figPatt;
	private List<Pattern> subFigPatt = new ArrayList<Pattern>();

	Map<String, Map<String, Integer>> table = new HashMap<String, Map<String, Integer>>();

	public void initialize(UimaContext context) throws ResourceInitializationException {

		super.initialize(context);

		if (this.clauseLevelStr != null && this.clauseLevelStr.toLowerCase().equals("true")) {
			clauseLevel = true;
		}

		this.collection = new BioCCollection();

		//
		// A List of regular expressions to recognize
		// all figure legend codes appearing in text.
		//
		this.figPatt = Pattern.compile("\\s*[Ff]ig(ure|.){0,1}\\s+(\\d+)");

		// this.subFigPatt.add(Pattern.compile("\\s*(\\d+)\\s*\\p{Punct}*\\s*\\w{2,}"));

		//
		// 1. Delineated by brackets
		this.subFigPatt.add(Pattern.compile("^\\s*\\(\\s*([A-Za-z])\\s*\\)"));

		// 2. Simple single alphanumeric codes, followed by punctuation.
		// this.subFigPatt.add(Pattern.compile("^\\s*(\\s*[A-Za-z]\\s*)\\p{Punct}"));

	}

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
		if (uiD.getId().equals("skip"))
			return;

		for (UimaBioCAnnotation a : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, uiD)) {
			
			Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
			if (!infons.get("type").equals("formatting") || !infons.get("value").equals("fig"))
				continue;

			String fig = "";
			String subFig = "-";
			String oldSubFig = "-";
			Matcher m = figPatt.matcher(a.getCoveredText());
			if (m.find()) {
				fig = m.group(2);
			}

			if (fig.equals(""))
				continue;

			String txt = "";
			int startPos = 0, endPos = 0;
			for (Sentence s : JCasUtil.selectCovered(Sentence.class, a)) {
				
				String sText = this.readTokenizedText(jCas, s);

				String subFigCode = readSubFigCodes(jCas, s);
				if (!subFigCode.equals("-"))
					subFig = subFigCode;
				
				if( subFig.equals("-") )
					continue;

				if (!subFig.equals(oldSubFig)) {
					if (!oldSubFig.equals("-")) {
						Map<String, String> inf = new HashMap<String, String>();
						inf.put("type", "subfigure-caption");
						inf.put("value", "f" + fig + oldSubFig.toLowerCase() );
						UimaBioCAnnotation figA = UimaBioCUtils.createNewAnnotation(jCas, startPos, endPos, inf);
					}
					startPos = s.getBegin();
					txt = "";
					oldSubFig = subFig;
				}

				if (sText.endsWith("."))
					txt += " " + sText;
				else
					txt += ". " + sText;
				endPos = s.getEnd();
				
			}

			if (!subFig.equals("-")) {
				Map<String, String> inf = new HashMap<String, String>();
				inf.put("type", "subfigure-caption");
				inf.put("value", "f" + fig + subFig.toLowerCase() );
				UimaBioCAnnotation figA = UimaBioCUtils.createNewAnnotation(jCas, startPos, endPos, inf);
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
