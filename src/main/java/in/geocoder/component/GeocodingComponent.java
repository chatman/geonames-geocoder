package in.geocoder.component;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.hadoop.util.bloom.BloomFilter;
import org.apache.hadoop.util.bloom.Key;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.Version;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.StrField;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.request.SimpleFacets.CountPair;
import org.apache.solr.util.BoundedTreeSet;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;

import in.geocoder.component.geocoder.Classification;
import in.geocoder.component.geocoder.FilterSet;
import in.geocoder.component.geocoder.GeocoderResult;
import in.geocoder.component.geocoder.Permutation;
import in.geocoder.component.geocoder.util.AllCombinationIteratable;
import in.geocoder.component.geocoder.util.OrderedChoiceIterable;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class GeocodingComponent extends SearchComponent {
	public static final String COMPONENT_NAME = "geocoder";

	private FilterSet filterSet;
	private Map<Character, String> hierarchicalFields = null;
	private List<Character> hierarchyList;
	private Map<Character, String> otherFields = null;
	private Set<String> grammar = null;
	private String levelField = null;
	private String geoField = null;

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void init(NamedList args) {
		super.init(args);
		hierarchicalFields = new LinkedHashMap<Character, String>();
		hierarchyList = new ArrayList<Character>();
		NamedList<String> hFields = (NamedList<String>)args.get("hierarchicalFields");
		for (Map.Entry<String, String> e: hFields) {
			hierarchicalFields.put(e.getKey().charAt(0), e.getValue());
			hierarchyList.add(e.getKey().charAt(0));
		}
		
		otherFields = new LinkedHashMap<Character, String>();
		NamedList<String> oFields = (NamedList<String>)args.get("otherFields");
		for (Map.Entry<String, String> e: oFields)
			otherFields.put(e.getKey().charAt(0), e.getValue());
		
		grammar = new LinkedHashSet<String>();
		List<String> g = (List<String>)args.get("grammar");
		for (String s: g)
			grammar.add(s);
		
		levelField = (String) args.get("levelField");
		geoField = (String) args.get("geoField");		
	}

	@Override
	public void prepare(ResponseBuilder rb) throws IOException {
		if (filterSet==null) {
			filterSet = new FilterSet();

			for (Character symbol: hierarchicalFields.keySet()) {
				String field = hierarchicalFields.get(symbol);
				NamedList<Integer> termFreqs = getTerms(rb.req.getSearcher(), rb.req.getSchema(), field);
				Set<String> terms = new HashSet<String>();
				for (Map.Entry<String, Integer> e: termFreqs)
					terms.add(e.getKey());
				filterSet.addFilter(field, symbol, terms);
			}
			for (Character symbol: otherFields.keySet()) {
				String field = hierarchicalFields.get(symbol);
				NamedList<Integer> termFreqs = getTerms(rb.req.getSearcher(), rb.req.getSchema(), field);
				Set<String> terms = new HashSet<String>();
				for (Map.Entry<String, Integer> e: termFreqs)
					terms.add(e.getKey());
				filterSet.addFilter(field, symbol, terms);
			}
		}
	}

	private NamedList<Integer> getTerms(SolrIndexSearcher searcher, IndexSchema schema, String field) throws IOException {
		NamedList<Object> termsResult = new SimpleOrderedMap<Object>();

		boolean sort = true;

		boolean raw = false;


		final AtomicReader indexReader = searcher.getAtomicReader();
		Fields lfields = indexReader.fields();

		NamedList<Integer> fieldTerms = new NamedList<Integer>();
		termsResult.add(field, fieldTerms);

		Terms terms = lfields == null ? null : lfields.terms(field);
		if (terms == null) {
			// no terms for this field
			return new NamedList<Integer>();
		}

		FieldType ft = raw ? null : schema.getFieldTypeNoEx(field);
		if (ft==null) ft = new StrField();

		TermsEnum termsEnum = terms.iterator(null);
		BytesRef term = null;


		term = termsEnum.next();

		BoundedTreeSet<CountPair<BytesRef, Integer>> queue = (sort ? new BoundedTreeSet<CountPair<BytesRef, Integer>>(Integer.MAX_VALUE) : null);
		CharsRef external = new CharsRef();
		while (term != null) {
			boolean externalized = false; // did we fill in "external" yet for this term?

			// This is a good term in the range.  Check if mincount/maxcount conditions are satisfied.
			int docFreq = termsEnum.docFreq();
			// add the term to the list
			if (sort) {
				queue.add(new CountPair<BytesRef, Integer>(BytesRef.deepCopyOf(term), docFreq));
			} else {
				// TODO: handle raw somehow
				if (!externalized) {
					ft.indexedToReadable(term, external);
				}
				fieldTerms.add(external.toString(), docFreq);
			}


			term = termsEnum.next();
		}

		if (sort) {
			for (CountPair<BytesRef, Integer> item : queue) {
				ft.indexedToReadable(item.key, external);          
				fieldTerms.add(external.toString(), item.val);
			}
		}

		return fieldTerms;
	}

	@Override
	public void process(ResponseBuilder rb) throws IOException {
		String query = rb.req.getParams().get("q", null);
		
		if (query==null)
			return;
		
		Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_46,
				new CharArraySet(Version.LUCENE_46, new HashSet<String>(), false));
		List<String> tokens = tokenize(query, analyzer);

		List<Classification> classifications = classify (tokens, filterSet);

		List<Permutation> permutations = getPermutations(tokens, classifications);

		List<Permutation> validPermutations = validate (rb.req.getSearcher(), permutations);
		
		List<GeocoderResult> results = getDocuments (rb.req.getSearcher(), validPermutations);

		List<NamedList<String>> output = new ArrayList<NamedList<String>>();
		for (GeocoderResult r: results) {
			NamedList<String> nl = r.getFields();
			nl.add("FullAnnotation", r.getPermutation().fullAnnotation);
			nl.add("ShortAnnotation", r.getPermutation().shortAnnotation);
			output.add(nl);
		}
		
		rb.rsp.add("results", output);
		rb.rsp.add("permutations", permutations);
		rb.rsp.add("validPermutations", validPermutations);
	}

	private List<String> tokenize(String query, Analyzer analyzer) throws IOException {
		TokenStream tokenStream = analyzer.tokenStream(null, new StringReader(query));
		CharTermAttribute charTermAttr = (CharTermAttribute)tokenStream.addAttribute(CharTermAttribute.class);
		List<String> tokens = new ArrayList<String>();

		tokenStream.reset();
		while (tokenStream.incrementToken())
		{
			String term = charTermAttr.toString();
			String text = term;
			if (text != null)
				tokens.add(term);
		}
		return tokens;
	}

	private List<Classification> classify(List<String> queryTokens, FilterSet filterSet) {
		int cntTokens = queryTokens.size();
		int maxToken = cntTokens > 15 ? 15 : cntTokens;

		Integer[] tokenPositions = new Integer[maxToken];

		for (int i = 0; i < maxToken; i++) 
			tokenPositions[i] = Integer.valueOf(i);

		List<Classification> classificationsList = new ArrayList<Classification>();

		OrderedChoiceIterable orderedChoiceIterable = new OrderedChoiceIterable(tokenPositions);
		for (Integer[] tokenPos : orderedChoiceIterable)
			if (tokenPos != null) {
				StringBuilder sb = new StringBuilder();
				TreeSet<Integer> tokenPositions1 = new TreeSet<Integer>();
				for (int k = 0; k < tokenPos.length; k++) {
					sb.append((String)queryTokens.get(tokenPos[k].intValue()) + " ");
					tokenPositions1.add(tokenPos[k]);
				}

				String searchTerm = sb.toString().trim();
				if (searchTerm.length() != 0)
				{
					for (String field: filterSet.getFilters()) {
						BloomFilter f = filterSet.getFilter(field);
						char symbol = filterSet.getSymbol(field);
						if (f.membershipTest(new Key(searchTerm.getBytes()))) 
							classificationsList.add(new Classification(field, symbol, searchTerm, Arrays.asList(tokenPos)));
					}
				}
			}
		return classificationsList;
	}

	private List<Permutation> getPermutations(List<String> queryTokens, List<Classification> classifications) {
		LinkedHashMap<List<Integer>, List<Classification>> map = new LinkedHashMap<List<Integer>, List<Classification>>();
		for (Classification c : classifications) {
			List<Classification> values = map.containsKey(c.tokenPositions) ? (List<Classification>)map.get(c.tokenPositions) : new ArrayList<Classification>();
			values.add(c);
			map.put(c.tokenPositions, values);
		}

		List<Classification[]> matrix = new ArrayList<Classification[]>();
		for (List<Integer> key: map.keySet()) {
			List<Classification> list = map.get(key);
			list.add(null);
			Classification[] arr = new Classification[list.size()];
			arr = (Classification[])list.toArray(arr);
			matrix.add(arr);
		}

		Classification[] combination = new Classification[map.size()];
		AllCombinationIteratable<Classification> iterator = new AllCombinationIteratable<Classification>(matrix, combination);

		List<Permutation> perms = new ArrayList<Permutation>();

		while (iterator.hasNext()) {
			combination = (Classification[])iterator.next();

			int counter = 0;
			Set<Integer> tokenSet = new LinkedHashSet<Integer>();
			for (Classification c : combination) {
				if (c != null) {
					counter += c.tokenPositions.size();
					tokenSet.addAll(c.tokenPositions);
				}
			}
			if (counter == tokenSet.size()) {
				Permutation perm = new Permutation(combination, queryTokens);
				perms.add(perm);
			}
		}

		for (int i = 0; i < perms.size(); i++) {
			if (grammar.contains(perms.get(i).shortAnnotation)==false) {
				perms.remove(i);
				i--;
			}
		}

		Collections.sort(perms, comparator);

		return perms;
	}
	
	private Comparator<Permutation> comparator = new Comparator<Permutation>() {

		public int compare(Permutation o1, Permutation o2) {
			int targetUnmatched = 0; int sourceUnmatched = 0;
			for (char c : o2.shortAnnotation.toCharArray())
				if (c == '.')
					targetUnmatched++;
			for (char c : o1.shortAnnotation.toCharArray())
				if (c == '.')
					sourceUnmatched++;
			
			if (targetUnmatched != sourceUnmatched)
				return sourceUnmatched - targetUnmatched;
			
			int grammarIndex1 = Integer.MAX_VALUE, grammarIndex2 = Integer.MAX_VALUE;
			int i=0;
			for (String g: grammar) {
				if (g.equals(o1.shortAnnotation))
					grammarIndex1 = i;
				if (g.equals(o2.shortAnnotation))
					grammarIndex2 = i;
				i++;
			}
			if(grammarIndex1 != grammarIndex2)
				return grammarIndex1 - grammarIndex2;
			
			targetUnmatched = 0; sourceUnmatched = 0;
			for (char c : o2.fullAnnotation.toCharArray())
				if (c == '.')
					targetUnmatched++;
			for (char c : o1.fullAnnotation.toCharArray())
				if (c == '.')
					sourceUnmatched++;
			
			return sourceUnmatched - targetUnmatched;
		}
	};

	private List<Permutation> validate(SolrIndexSearcher searcher, List<Permutation> permutations) {
		List<Permutation> validPerms = new ArrayList<Permutation>();

		for(Permutation p: permutations) {

			BooleanQuery bq = new BooleanQuery();
			int level = -1;
			for (Classification c : p.classifications) {
				if (c != null) {
					String fld = c.classification;
					bq.add(new TermQuery(new Term(fld, c.text)), Occur.MUST);
					level = Math.max(level, hierarchyList.indexOf(c.symbol));
				}
			}
			if (level!=-1)
				bq.add(new TermQuery(new Term(levelField, Character.toString(hierarchyList.get(level)))), Occur.MUST);

			try {
				if(searcher.search(bq, 1).totalHits>0) {
					p.query = bq;
					validPerms.add(p);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return validPerms;
	}
	
	private List<GeocoderResult> getDocuments(SolrIndexSearcher searcher, List<Permutation> permutations) {
		List<GeocoderResult> gcResults = new ArrayList<GeocoderResult>();
		
		for(Permutation p: permutations) {
			if (p.query==null)
				continue;
			try {
				TopDocs results = searcher.search(p.query, 1);
				if(results.totalHits>0) {
					Document doc = searcher.doc(results.scoreDocs[0].doc);
					GeocoderResult gcRes = new GeocoderResult();
					
					gcRes.setPermutation(p);
					StringBuilder unmatched = new StringBuilder();
					for (int i = 0; i < p.fullAnnotation.length(); i++) 
						if (p.fullAnnotation.charAt(i) == '.')
							unmatched.append(p.queryTokens.get(i) + " ");
					
					gcRes.addField("unmatched", unmatched.toString().trim());
					for (String field: hierarchicalFields.values())
						if(doc.get(field)!=null)
							gcRes.addField(field, doc.get(field));
					for (String field: otherFields.values())
						if(doc.get(field)!=null)
							gcRes.addField(field, doc.get(field));
					
					gcRes.setUnmatched(unmatched.toString().trim());
					gcRes.addField(geoField, doc.get(geoField));
					gcResults.add(gcRes);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return gcResults;
	}

	@Override
	public String getDescription() {
		return null;
	}

	@Override
	public String getSource() {
		return null;
	}

}
