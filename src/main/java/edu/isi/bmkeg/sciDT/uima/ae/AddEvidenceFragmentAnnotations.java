package edu.isi.bmkeg.sciDT.uima.ae;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.resource.ResourceInitializationException;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.util.JCasUtil;

import bioc.BioCAnnotation;
import bioc.BioCLocation;
import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import bioc.type.UimaBioCPassage;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;

public class AddEvidenceFragmentAnnotations extends JCasAnnotator_ImplBase {

	private static Logger logger = Logger.getLogger(AddEvidenceFragmentAnnotations.class);
	
	public void initialize(UimaContext context) throws ResourceInitializationException {

		super.initialize(context);
		
	}

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		try {

			UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
			if (uiD.getId().equals("skip"))
				return;

			logger.info("Building Evidence Fragments for " + uiD.getId());

			UimaBioCPassage docP = UimaBioCUtils.readDocument(jCas);

			//
			// Generate new passages for each fragment
			//
			Map<String,BioCAnnotation> frgs = new HashMap<String,BioCAnnotation>();
			for (UimaBioCAnnotation uiA : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, uiD)) {
				Map<String, String> infons = UimaBioCUtils.convertInfons(uiA.getInfons());
				if(!infons.get("type").equals("rubicon") || 
						!infons.get("value").equals("clause") || 
						!infons.containsKey("scidp-fig-assignment") )
					continue;
				String[] figCodes = infons.get("scidp-fig-assignment").split("\\|");
				for( String figCode : figCodes ) {
					if( !frgs.containsKey(figCode) ) {
						BioCAnnotation a = createFragmentPassage(uiA, figCode);
						frgs.put(figCode, a);				
					} else {
						BioCAnnotation a = frgs.get(figCode);
						List<BioCLocation> ll = a.getLocations();
						BioCLocation l = ll.get(ll.size()-1);
						int end = l.getOffset() + l.getLength();
						String newText = a.getText() + uiA.getCoveredText();
						a.setText(newText);
						if( uiA.getBegin() == end+1 ) {
							l.setLength(uiA.getEnd() - l.getOffset());
						} else {
							BioCLocation l2 = new BioCLocation();
							l2.setOffset(uiA.getBegin());
							l2.setLength(uiA.getEnd() - uiA.getBegin());
							ll.add(l2);
						}
					}	
				}
			}

			FSArray existingAnnotations = docP.getAnnotations();
			int j = existingAnnotations.size();
			FSArray newAnnotations = new FSArray(jCas, frgs.size() + j);
			for (int i=0; i<j; i++) {
				newAnnotations.set(i, existingAnnotations.get(i));				
			}
			for (String key : frgs.keySet() ) {
				BioCAnnotation a = frgs.get(key);
				UimaBioCAnnotation uiA = UimaBioCUtils.convertBioCAnnotation(a, jCas);
				newAnnotations.set(j, uiA);
				j++;
			}
			docP.setAnnotations(newAnnotations);
			
			logger.info("Building Evidence Fragments for " + uiD.getId() + " - COMPLETED");

		} catch (Exception e) {

			throw new AnalysisEngineProcessException(e);

		}

	}

	private BioCAnnotation createFragmentPassage(UimaBioCAnnotation uiA, String figCode) {
		BioCAnnotation a = new BioCAnnotation();
		BioCLocation l = new BioCLocation();
		l.setOffset(uiA.getBegin());
		l.setLength(uiA.getEnd() - uiA.getBegin());
		a.addLocation(l);
		Map<String, String> inf = a.getInfons();
		inf.put("type", "evidence-fragment");
		inf.put("value", figCode);
		return a;
	}
	
}
