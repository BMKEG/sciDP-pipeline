package edu.isi.bmkeg.sciDT.uima.ae;

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
import edu.isi.bmkeg.sciDT.drools.ClauseFeatures;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;

public class HeuristicExperimentLabeling extends JCasAnnotator_ImplBase {

	private static Logger logger = Logger.getLogger(HeuristicExperimentLabeling.class);
	private BioCCollection collection;

	Map<String, Map<String, Integer>> table = new HashMap<String, Map<String, Integer>>();

	public void initialize(UimaContext context) throws ResourceInitializationException {

		super.initialize(context);

		this.collection = new BioCCollection();

	}

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
		if (uiD.getId().equals("skip"))
			return;

		String id = uiD.getId();

		List<UimaBioCAnnotation> outerAnnotations = JCasUtil.selectCovered(UimaBioCAnnotation.class, uiD);
		
		try {

			this.buildDataStructures(jCas, uiD.getBegin(), uiD.getEnd());

		} catch (Exception e) {
			
			throw new AnalysisEngineProcessException(e);
			
		}

	}

	private void buildDataStructures(JCas jCas, int start, int end) throws Exception {

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
		Map<UimaBioCAnnotation, ClauseFeatures> fMap = new HashMap<UimaBioCAnnotation, ClauseFeatures>();

		List<UimaBioCAnnotation> clauseList = new ArrayList<UimaBioCAnnotation>();
		List<Integer> startingPoints = new ArrayList<Integer>();
		int i = 0;
		
		
		SENTENCE_LOOP: for (Sentence s : sentences) {

			//
			// Identify exLinks, inLinks or headers
			//
			Map<Integer, Integer> currLvl = new HashMap<Integer, Integer>();

			Set<String> codes = new HashSet<String>();

			for (UimaBioCAnnotation a : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, s)) {
				Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
				if (infons.get("type").equals("rubicon") && infons.get("value").equals("clause")) {

					fMap.put(a, new ClauseFeatures(jCas, a));
					
					if (!infons.get("scidp-experiment-labels").equals("[]"))
						startingPoints.add(i);
					
					clauseList.add(a);
					i++;

				}
			}
		
		}
		
		STARTING_POINT_LOOP: for (int ii : startingPoints) {
			
			UimaBioCAnnotation sp = clauseList.get(ii);
			ClauseFeatures f = fMap.get(sp);
			String exptLabel = f.readLocalExptLabel();
			
			//
			// Trace backward until we hit a boundary condition
			//
			UimaBioCAnnotation next = sp;
			ClauseFeatures nf = new ClauseFeatures(jCas, next);
			UimaBioCAnnotation prev = clauseList.get(ii-1);
			ClauseFeatures pf = new ClauseFeatures(jCas, prev);
			int jj = 1;
			
			while( !nf.isExperimentalBoundaryTransition(pf, false) ) {
				if( ii - jj < 1) 
					break;
				
				next = clauseList.get(ii-jj);
				prev = clauseList.get(ii-jj-1);
				jj++;
				
				Map<String, String> inf = UimaBioCUtils.convertInfons(next.getInfons());
				if( !inf.get("scidp-experiment-labels").equals("[]") )
					break;
				inf.put("scidp-experiment-labels", exptLabel);
				next.setInfons(UimaBioCUtils.convertInfons(inf, jCas));
				nf = new ClauseFeatures(jCas, next);
				pf = new ClauseFeatures(jCas, prev);
			}			
			
			//
			// Is the next clause a boundary condition?
			//
			next = clauseList.get(ii+1);
			Map<String, String> inf = UimaBioCUtils.convertInfons(next.getInfons());
			
			nf = new ClauseFeatures(jCas, next);
			pf = new ClauseFeatures(jCas, sp);
			
			if( nf.isExperimentalBoundaryTransition(pf, true) || 
					!inf.get("scidp-experiment-labels").equals("[]"))
				continue STARTING_POINT_LOOP;
			
			inf.put("scidp-experiment-labels", exptLabel);
			next.setInfons(UimaBioCUtils.convertInfons(inf, jCas));
		
			//
			// Trace forward until we hit a boundary condition
			//
			int kk = 2;
			while( !nf.isExperimentalBoundaryTransition(pf, true) ) {
				if( ii+kk >= clauseList.size() ) 
					break;
				
				next = clauseList.get(ii+kk);
				prev = clauseList.get(ii+kk-1);
				inf = UimaBioCUtils.convertInfons(next.getInfons());
				if( !inf.get("scidp-experiment-labels").equals("[]") )
					break;
				inf.put("scidp-experiment-labels", exptLabel);
				next.setInfons(UimaBioCUtils.convertInfons(inf, jCas));
				nf = new ClauseFeatures(jCas, next);
				pf = new ClauseFeatures(jCas, prev);
			}
						
		}

	}

}
