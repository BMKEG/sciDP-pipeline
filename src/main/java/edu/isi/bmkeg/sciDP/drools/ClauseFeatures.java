package edu.isi.bmkeg.sciDP.drools;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.uima.jcas.JCas;
import org.uimafit.util.JCasUtil;

import bioc.type.UimaBioCAnnotation;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;

public class ClauseFeatures {

	private UimaBioCAnnotation clause;
	private JCas jCas;
	private BIO bioCode;
	private CosidCode cosidCode;
	private String exptLabel;
	
	public enum BIO {
	    B, I, O
	}

	public enum CosidCode {
	    Context, Experiment, Interpretation
	}
	
	Pattern headerPattern = Pattern.compile("header\\-([\\?\\d]+)");

	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	
	public ClauseFeatures(JCas jcas, UimaBioCAnnotation clause) throws Exception {
		
		Map<String, String> infons = UimaBioCUtils.convertInfons(clause.getInfons());
		if (!infons.get("type").equals("rubicon") || !infons.get("value").equals("clause"))
			throw new Exception("Cannot construct ClauseFeatures on non-clause annotations");
		
		this.clause = clause;
		this.jCas = jcas;
	}
	
	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	public UimaBioCAnnotation getClause() {
		return clause;
	}

	public void setClause(UimaBioCAnnotation clause) {
		this.clause = clause;
	}

	public JCas getjCas() {
		return jCas;
	}

	public void setjCas(JCas jCas) {
		this.jCas = jCas;
	}

	public BIO getBioCode() {
		return bioCode;
	}

	public void setBioCode(BIO bioCode) {
		this.bioCode = bioCode;
	}

	public CosidCode getCosidCode() {
		return cosidCode;
	}

	public void setCosidCode(CosidCode cosidCode) {
		this.cosidCode = cosidCode;
	}

	public String getExptLabel() {
		return exptLabel;
	}

	public void setExptLabel(String exptLabel) {
		this.exptLabel = exptLabel;
	}
	
	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	
	public String getTokenizedWordSequence() {
		
		return UimaBioCUtils.readTokenizedText(jCas, clause);
	
	}

	public boolean isInlink() {
		
		Map<String, String> infons = UimaBioCUtils.convertInfons(clause.getInfons());
		String inExHeading = infons.get("scidp-inExHeading-string");
		if( inExHeading != null && inExHeading.contains("inLink") )
			return true;
		else 
			return false;

	}

	public boolean isExlink() {
		
		Map<String, String> infons = UimaBioCUtils.convertInfons(clause.getInfons());
		String inExHeading = infons.get("scidp-inExHeading-string");
		if( inExHeading != null &&  inExHeading.contains("exLink") )
			return true;
		else 
			return false;

	}
	
	public boolean isHeader() {
		
		Map<String, String> infons = UimaBioCUtils.convertInfons(clause.getInfons());
		String inExHeading = infons.get("scidp-inExHeading-string");
		
		if( inExHeading != null && inExHeading.contains("header") )  
			return true;
		else 
			return false;	
		
	}
	
	public String readHeaderLevel() {
		
		Map<String, String> infons = UimaBioCUtils.convertInfons(clause.getInfons());
		String inExHeading = infons.get("scidp-inExHeading-string");
		
		Matcher m = headerPattern.matcher(inExHeading);
		if( m.find() ) 
			return m.group(1);
		else 
			return "";	
		
	}
	
	public String getParagraphNumber() {
		
		Map<String, String> infons = UimaBioCUtils.convertInfons(clause.getInfons());
		return infons.get("scidp-paragraph-number");
				
	}

	public String getSentenceNumber() {
		
		Map<String, String> infons = UimaBioCUtils.convertInfons(clause.getInfons());
		return infons.get("scidp-sentence-number");
				
	}
	
	public boolean containsWord(String word) {
		
		Pattern p = Pattern.compile("\\s" + word + "\\s");
		Matcher m = p.matcher(this.getTokenizedWordSequence());

		return m.find();
	
	}
	
	public String getDiscourseType() {
		
		Map<String, String> infons = UimaBioCUtils.convertInfons(clause.getInfons());
		return infons.get("scidp-discourse-type");

	}
	
	public String readLocalExptLabel() {
		
		Map<String, String> infons = UimaBioCUtils.convertInfons(clause.getInfons());
		return infons.get("scidp-experiment-labels");

	}
	
	public String readHeadingString() {
		return UimaBioCUtils.readHeadingString(jCas, clause, "");
	}
	
	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	
	public String getPrecedingClausesDiscourseType() throws Exception {
		
		UimaBioCAnnotation precede = UimaBioCUtils.readPrecedingClause(jCas, clause);
		Map<String, String> infons = UimaBioCUtils.convertInfons(precede.getInfons());
		return infons.get("scidp-discourse-type");

	}
	
	public String getFollowingClausesDiscourseType() {
		
		UimaBioCAnnotation follow = UimaBioCUtils.readFollowingClause(jCas, clause);
		Map<String, String> infons = UimaBioCUtils.convertInfons(follow.getInfons());
		return infons.get("scidp-discourse-type");

	}
	
	
	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	public String getLastExperimentalLabel() throws Exception {
		
		UimaBioCAnnotation precede = UimaBioCUtils.readPrecedingClause(jCas, clause);
		ClauseFeatures pf = new ClauseFeatures(jCas, precede);
	
		while( pf.readHeadingString().toLowerCase().startsWith("results") && 
				pf.readLocalExptLabel() != null &&
				pf.readLocalExptLabel().equals("[]") ) {
			precede = UimaBioCUtils.readPrecedingClause(jCas, precede);
			pf = new ClauseFeatures(jCas, precede);
		}
		
		return pf.readLocalExptLabel();
		
	}
	
	
	public String getNextExperimentalLabel() {
		
		UimaBioCAnnotation follow = UimaBioCUtils.readFollowingClause(jCas, clause);
		Map<String, String> infons = UimaBioCUtils.convertInfons(follow.getInfons());
		
		while( infons.get("scidp-experiment-labels").equals("[]") || follow == null ) {
			follow = UimaBioCUtils.readFollowingClause(jCas, follow);
			infons = UimaBioCUtils.convertInfons(follow.getInfons());
		}
		
		return infons.get("scidp-experiment-labels");
		
	}
	
	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	/*public UimaBioCAnnotation getLastBoundary() throws Exception {
		
		UimaBioCAnnotation next = clause;
		ClauseFeatures nf = new ClauseFeatures(jCas, next);
		while( !nf.isExperimentalBoundaryTransition(false) ) {
			next = UimaBioCUtils.readPrecedingClause(jCas, next);
			nf = new ClauseFeatures(jCas, next);
		}

		return next;
		
	}

	public UimaBioCAnnotation getNextBoundary() throws Exception {
		
		UimaBioCAnnotation next = UimaBioCUtils.readFollowingClause(jCas, clause);
		ClauseFeatures nf = new ClauseFeatures(jCas, next);
		while( !nf.isExperimentalBoundaryTransition(true) ) {
			next = UimaBioCUtils.readFollowingClause(jCas, next);
			nf = new ClauseFeatures(jCas, next);
		}

		return next;
		
	} */
	
	public boolean isExperimentalBoundaryTransition(ClauseFeatures pf, boolean lookAhead) throws Exception {
		
		String pd = pf.getDiscourseType();
		String td = this.getDiscourseType();
		
		//
		// the experimental label of the last clause is different from this one
		//
		if( (this.isInlink() && lookAhead) || 
				(pf.isInlink() && !lookAhead)) {
			return true;
		}
		// If the last clause and this clause are in the same sentence, return false
		else if( pf.getSentenceNumber().equals(this.getSentenceNumber()) ) {
			return false;
		} 
		// this is a header and the last clause was not, demarking the end of a section
		else if( !pf.isHeader() && this.isHeader() ) {
			return true;
		} 
		// Similarly if the last clause is a header and this is not, 
		// demarking the start of a section 
		else if(  pf.isHeader() && !this.isHeader() ) {
			return true;
		}
		// the discourse type of the last clause is result/implication 
		// and next is citing another paper
		else if( (!pf.isExlink() && (pd.equals("result") || pd.equals("implication"))) &&
				 (this.isExlink() && (td.equals("result") || td.equals("implication")))
				 ) {
			return true;
		}
		// the discourse type of the last clause is result/implication and next is fact|hypothesis|problem|goal|method
		else if( (!pf.isHeader() && !pf.isExlink() && (pd.equals("result") || pd.equals("implication"))) &&
				 (!this.isHeader() && (td.equals("fact") || td.equals("hypothesis") || 
								 td.equals("problem") || td.equals("goal") || 
								 td.equals("method")))
				 ) {
			return true;
		} 
		//
		// the discourse type of the last clause is result/implication 
		// and next is goal/method
		//
		else if( (pd.equals("result") || pd.equals("implication")) &&
						 (td.equals("goal") || td.equals("method"))) {
			return true;
		}

				
		return false;

	}
	
}
