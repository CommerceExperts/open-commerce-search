package de.cxp.ocs.smartsuggest.querysuggester;

import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.WordlistLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.cxp.ocs.smartsuggest.querysuggester.lucene.LuceneQuerySuggester;
import de.cxp.ocs.smartsuggest.querysuggester.modified.ModifiedTermsService;
import de.cxp.ocs.smartsuggest.spi.SuggestRecord;
import lombok.extern.slf4j.Slf4j;

@SuppressWarnings("deprecation")
@Slf4j
class LuceneQuerySuggesterTest {

	private LuceneQuerySuggester	underTest;
	private ModifiedTermsService	modifiedTermsService	= mock(ModifiedTermsService.class);

	@BeforeEach
	void beforeEach(@TempDir Path indexFolder) throws IOException {
		System.setProperty("alwaysDoFuzzy", "true");
		underTest = new LuceneQuerySuggester(indexFolder, Locale.ROOT, modifiedTermsService, getWordSet(Locale.ROOT));
	}

	@AfterEach
	void close() throws Exception {
		underTest.close();
	}

	@DisplayName("Search for 'user' should return the best matches for both 'user query 1' and 'user query 2', sorted by the weight")
	@Test
	void suggest0() {
		final String masterQuery1 = "master query 1";
		final String masterQuery2 = "master query 2";
		List<SuggestRecord> toIndex = new ArrayList<>(asList(
				asSuggestRecord("user query 1", masterQuery1, 100),
				asSuggestRecord("u 1", masterQuery2, 100),
				asSuggestRecord("user query 2", masterQuery2, 101),
				asSuggestRecord("u 2", masterQuery2, 101)));

		underTest.index(toIndex).join();

		List<QuerySuggester.Result> results = underTest.suggest("user");

		assertThat(results).hasSize(1);
		final QuerySuggester.Result result = results.get(0);
		assertThat(result.getName()).isEqualTo(LuceneQuerySuggester.TYPO_MATCHES_GROUP_NAME);
		final List<String> suggestions = result.getSuggestions();
		assertThat(suggestions).element(0).isEqualTo(masterQuery2);
		assertThat(suggestions).element(1).isEqualTo(masterQuery1);
	}

	@DisplayName("Search for 'user' should return the best matches for both 'user query 1' and 'user query 2'. Since the master query is the same it will return it just once")
	@Test
	void suggest1() {
		final String masterQuery1 = "master query 1";
		final String masterQuery2 = "master query 2";
		List<SuggestRecord> toIndex = new ArrayList<>(asList(
				asSuggestRecord("user query 1", masterQuery1, 100),
				asSuggestRecord("u 1", masterQuery2, 100),
				asSuggestRecord("user query 2", masterQuery1, 101),
				asSuggestRecord("u 2", masterQuery2, 101)));

		underTest.index(toIndex).join();

		List<QuerySuggester.Result> results = underTest.suggest("user");

		assertThat(results).hasSize(1);
		final QuerySuggester.Result bestMatchesResult = results.get(0);
		assertThat(bestMatchesResult.getName()).isEqualTo(LuceneQuerySuggester.TYPO_MATCHES_GROUP_NAME);
		final List<String> suggestions = bestMatchesResult.getSuggestions();
		assertThat(suggestions).element(0).isEqualTo(masterQuery1);
	}

	@DisplayName("Search for 'u' should return the best matches for all suggestions, sorted by the weight")
	@Test
	void suggest2() {
		final String masterQuery1 = "master query 1";
		final String masterQuery2 = "master query 2";
		final String masterQuery3 = "master query 3";
		final String masterQuery4 = "master query 4";
		List<SuggestRecord> toIndex = new ArrayList<>(asList(
				asSuggestRecord("user query 1", masterQuery1, 1000),
				asSuggestRecord("u 2", masterQuery2, 100),
				asSuggestRecord("user query 3", masterQuery3, 101),
				asSuggestRecord("u 4", masterQuery4, 99)));

		underTest.index(toIndex).join();

		List<QuerySuggester.Result> suggestions = underTest.suggest("u");

		assertThat(suggestions).hasSize(1);

		final QuerySuggester.Result bestMatchResults = suggestions.get(0);
		assertThat(bestMatchResults.getName()).isEqualTo(LuceneQuerySuggester.TYPO_MATCHES_GROUP_NAME);

		final List<String> bestMatchSuggestions = bestMatchResults.getSuggestions();
		assertThat(bestMatchSuggestions).element(0).isEqualTo(masterQuery1);
		assertThat(bestMatchSuggestions).element(1).isEqualTo(masterQuery3);
		assertThat(bestMatchSuggestions).element(2).isEqualTo(masterQuery2);
		assertThat(bestMatchSuggestions).element(3).isEqualTo(masterQuery4);
	}

	@DisplayName("Search for 'guci' should return properly spelled 'Gucci'")
	@Test
	void suggest_fuzzy_0() {
		final String Gucci = "Gucci";
		List<SuggestRecord> toIndex = new ArrayList<>(singletonList(
				asSuggestRecord("gucci", Gucci, 1000)));

		underTest.index(toIndex).join();

		List<QuerySuggester.Result> suggestions = underTest.suggest("guci");

		assertThat(suggestions).hasSize(1);
		final QuerySuggester.Result result = suggestions.get(0);
		assertThat(result.getName()).isEqualTo(LuceneQuerySuggester.FUZZY_MATCHES_ONE_EDIT_GROUP_NAME);
		final List<String> fuzzySuggestions = result.getSuggestions();
		assertThat(fuzzySuggestions).hasSize(1);
		assertThat(fuzzySuggestions).element(0).isEqualTo(Gucci);
	}

	@DisplayName("Search for 'veshin' should return properly spelled 'Washington'")
	@Test
	void suggest_fuzzy_1() {
		final String Washington = "washington";
		List<SuggestRecord> toIndex = new ArrayList<>(singletonList(
				asSuggestRecord("washington", Washington, 1000)));

		underTest.index(toIndex).join();

		List<QuerySuggester.Result> results = underTest.suggest("veshin");

		assertThat(results).hasSize(1);
		final QuerySuggester.Result fuzzyMatchesResult = results.get(0);
		assertThat(fuzzyMatchesResult.getName()).isEqualTo(LuceneQuerySuggester.FUZZY_MATCHES_TWO_EDITS_GROUP_NAME);
		final List<String> suggestions = fuzzyMatchesResult.getSuggestions();
		assertThat(suggestions).element(0).isEqualTo(Washington);
	}

	@DisplayName("Search for 'shirt' should return all unique best matches with 'shirt'")
	@Test
	void suggest_shirts() {
		final String mensShirts = "men's shirts";
		final String womensShirts = "women's shirts";
		List<SuggestRecord> toIndex = new ArrayList<>(asList(
				asSuggestRecord("men shirts", mensShirts, 101),
				asSuggestRecord("women shirt", womensShirts, 99),
				asSuggestRecord("shirt", mensShirts, 1000)));

		underTest.index(toIndex).join();

		List<QuerySuggester.Result> suggestions = underTest.suggest("shirt");

		assertThat(suggestions).hasSize(1);
		final QuerySuggester.Result bestMatchResult = suggestions.get(0);
		assertThat(bestMatchResult.getName()).isEqualTo(LuceneQuerySuggester.BEST_MATCHES_GROUP_NAME);
		final List<String> bestMatchSuggestions = bestMatchResult.getSuggestions();
		assertThat(bestMatchSuggestions).element(0).isEqualTo(mensShirts);
		assertThat(bestMatchSuggestions).element(1).isEqualTo(womensShirts);
	}

	@DisplayName("Search for `men's` should return all unique best matches with `men's`")
	@Test
	void suggest_mens() {
		final String mensShirts = "men's shirts";
		final String womensShirts = "women's shirts";
		final String withoutSMensShirts = "mens shirts" + " without 's";
		List<SuggestRecord> toIndex = new ArrayList<>(asList(
				asSuggestRecord(mensShirts, setOf("men's shirts"), 99),
				asSuggestRecord(womensShirts, setOf("women's shirt"), 101),
				asSuggestRecord(withoutSMensShirts, setOf("men shirt"), 1000)));

		underTest.index(toIndex).join();

		// with apostrophe
		List<QuerySuggester.Result> results = underTest.suggest("men's");
		assertThat(results).hasSize(3);
		assertThat(results).containsExactly(
				new QuerySuggester.Result(LuceneQuerySuggester.BEST_MATCHES_GROUP_NAME, singletonList(mensShirts)),
				new QuerySuggester.Result(LuceneQuerySuggester.FUZZY_MATCHES_ONE_EDIT_GROUP_NAME, singletonList(withoutSMensShirts)),
				new QuerySuggester.Result(LuceneQuerySuggester.FUZZY_MATCHES_TWO_EDITS_GROUP_NAME, singletonList(womensShirts)));

		// no apostrophe
		results = underTest.suggest("mens");

		assertThat(results).hasSize(2);
		assertThat(results).containsExactly(
				new QuerySuggester.Result(LuceneQuerySuggester.BEST_MATCHES_GROUP_NAME, singletonList(withoutSMensShirts)),
				new QuerySuggester.Result(LuceneQuerySuggester.FUZZY_MATCHES_ONE_EDIT_GROUP_NAME, asList(mensShirts)));

		// no space
		results = underTest.suggest("men'sshi");

		assertThat(results).hasSize(2);
		assertThat(results).containsExactly(
				new QuerySuggester.Result(LuceneQuerySuggester.FUZZY_MATCHES_ONE_EDIT_GROUP_NAME, asList(mensShirts)),
				new QuerySuggester.Result(LuceneQuerySuggester.FUZZY_MATCHES_TWO_EDITS_GROUP_NAME, asList(withoutSMensShirts))
		// women's shirts is not in the results because FuzzySuggester supports
		// at most 2 edits
		);
	}

	@Disabled("Lucene does not sort the way we want. More logic should be added in LuceneQuerySuggester.suggest")
	@DisplayName("Search for `new` should return first the phrases containing the word `new` and then words containing `new`")
	@Test
	void suggest_whole_word_first() {
		final String NewYork = "new york";
		final String Newton = "newton";
		final String Renew = "renew";
		List<SuggestRecord> toIndex = new ArrayList<>(asList(
				asSuggestRecord(NewYork, "", 100),
				asSuggestRecord(Renew, "", 100),
				asSuggestRecord(Newton, "", 101)));

		underTest.index(toIndex).join();

		List<QuerySuggester.Result> results = underTest.suggest("new");

		assertThat(results).hasSize(2);
		assertThat(results).isEqualTo(singletonList(
				new QuerySuggester.Result(LuceneQuerySuggester.BEST_MATCHES_GROUP_NAME, asList(NewYork, Newton))));
	}

	@DisplayName("Suggest with tags/contexts")
	@Test
	void suggest_with_context() {
		final String Movie1 = "movie 1";
		final String Movie2 = "movie 2";
		final String Book1 = "book 3";
		List<SuggestRecord> toIndex = new ArrayList<>(asList(
				asSuggestRecord("name 1", Movie1, 101, setOf("movie")),
				asSuggestRecord("name 2", Movie2, 100, setOf("movie")),
				asSuggestRecord("name 3", Book1, 100, setOf("book"))));

		underTest.index(toIndex).join();

		List<QuerySuggester.Result> bookResults = underTest.suggest("name", 10, setOf("book"));
		assertThat(bookResults).hasSize(1);
		assertThat(bookResults).containsExactly(
				new QuerySuggester.Result(LuceneQuerySuggester.TYPO_MATCHES_GROUP_NAME, singletonList(Book1)));

		List<QuerySuggester.Result> movieResults = underTest.suggest("name", 10, setOf("movie"));
		assertThat(movieResults).hasSize(1);
		assertThat(movieResults).containsExactly(
				new QuerySuggester.Result(LuceneQuerySuggester.TYPO_MATCHES_GROUP_NAME, asList(Movie1, Movie2)));
	}

	@DisplayName("When the search term is a phrase then it should return best matches first, then extra results for sub-phrase/words")
	@Test
	void suggest_multiterm_unordered() {
		final String MensShirts = "men's shirts";
		final String BeigeMensShirts = "beige men's shirts";
		final String WomensShirts = "women's shirts";
		final String WorkShirts = "work shirts";
		final String Shirts = "shirts";
		List<SuggestRecord> toIndex = new ArrayList<>(asList(
				asSuggestRecord(MensShirts, setOf("men shirts"), 101),
				asSuggestRecord(BeigeMensShirts, setOf("beige men shirts"), 100),
				asSuggestRecord(WomensShirts, setOf("women shirts"), 102),
				asSuggestRecord(WorkShirts, setOf("work shirts"), 103),
				asSuggestRecord(Shirts, setOf("shirts"), 104)));

		underTest.index(toIndex).join();

		List<QuerySuggester.Result> results = underTest.suggest("beige men's shirts");
		assertThat(results).hasSize(2);
		assertThat(results).containsExactly(
				new QuerySuggester.Result(LuceneQuerySuggester.BEST_MATCHES_GROUP_NAME, singletonList(BeigeMensShirts)),
				new QuerySuggester.Result(LuceneQuerySuggester.SHINGLE_MATCHES_GROUP_NAME, asList(Shirts, WorkShirts, WomensShirts, MensShirts)));
	}

	@DisplayName("When the search term is a phrase then it should return best matches first, then extra results for sub-phrase/words")
	@Test
	void suggest_multiterm_ordered() {
		final String MensShirts = "men's shirts";
		final String ShirtsMen = "shirts men";
		final String BeigeMensShirts = "beige men's shirts";
		final String WomensShirts = "women's shirts";
		final String WorkShirts = "work shirts";
		final String Shirts = "shirts";
		List<SuggestRecord> toIndex = new ArrayList<>(asList(
				asSuggestRecord("men shirts", MensShirts, 100),
				asSuggestRecord("shirts men", ShirtsMen, 100),
				asSuggestRecord("beige men shirts", BeigeMensShirts, 100),
				asSuggestRecord("women shirts", WomensShirts, 100),
				asSuggestRecord("work shirts", WorkShirts, 100),
				asSuggestRecord("shirts", Shirts, 100)));

		underTest.index(toIndex).join();

		List<QuerySuggester.Result> results = underTest.suggest("men's shi");

		assertThat(results).hasSize(3);
		assertThat(results).containsExactly(
				new QuerySuggester.Result(LuceneQuerySuggester.BEST_MATCHES_GROUP_NAME, asList(MensShirts, BeigeMensShirts)),
				new QuerySuggester.Result(LuceneQuerySuggester.FUZZY_MATCHES_TWO_EDITS_GROUP_NAME, asList(WomensShirts)),
				new QuerySuggester.Result(LuceneQuerySuggester.SHINGLE_MATCHES_GROUP_NAME, asList(ShirtsMen, Shirts, WorkShirts)));

		List<QuerySuggester.Result> results2 = underTest.suggest("shirts men");
		assertThat(results2).hasSize(2);
		assertThat(results2).containsExactly(
				new QuerySuggester.Result(LuceneQuerySuggester.BEST_MATCHES_GROUP_NAME, asList(ShirtsMen, MensShirts, BeigeMensShirts)),
				new QuerySuggester.Result(LuceneQuerySuggester.SHINGLE_MATCHES_GROUP_NAME, asList(Shirts, WorkShirts, WomensShirts)));
	}

	@DisplayName("Search for 'schuhe'")
	@Test
	void suggest3() {

		final String sportSchuheMaster = "sport schuhe";
		final String schuheForChindrenMaster = "schuhe für kinder";
		final String damenSommerSchuheMaster = "damen sommerschuhe";
		final String weißeSchuheMaster = "weiße schuhe";
		List<SuggestRecord> toIndex = new ArrayList<>(asList(
				asSuggestRecord("sport schuhe", sportSchuheMaster, 226000),
				asSuggestRecord("sportschue", sportSchuheMaster, 226000),
				asSuggestRecord("sport shuhe", sportSchuheMaster, 226000),
				asSuggestRecord("sport-schuhe", sportSchuheMaster, 226000),
				asSuggestRecord("sportshuhe", sportSchuheMaster, 226000),
				asSuggestRecord("spoerschuhe", sportSchuheMaster, 226000),
				asSuggestRecord("sport  schuhe", sportSchuheMaster, 226000),
				asSuggestRecord("sport schue", sportSchuheMaster, 226000),
				asSuggestRecord("sport. schue", sportSchuheMaster, 226000),
				asSuggestRecord("sporthschuhe", sportSchuheMaster, 226000),
				asSuggestRecord("sports schuhe", sportSchuheMaster, 226000),
				asSuggestRecord("sportschühe", sportSchuheMaster, 226000),
				asSuggestRecord("sportsschuhe", sportSchuheMaster, 226000),
				asSuggestRecord("sportschuhe", sportSchuheMaster, 226000),

				asSuggestRecord("schuhe  fur kinder", schuheForChindrenMaster, 3400),
				asSuggestRecord("schue für kinder", schuheForChindrenMaster, 3400),
				asSuggestRecord("schuh für kinder", schuheForChindrenMaster, 3400),
				asSuggestRecord("schuhe  für kinder", schuheForChindrenMaster, 3400),
				asSuggestRecord("schuhe fuer kinder", schuheForChindrenMaster, 3400),
				asSuggestRecord("schuhe fur kinder", schuheForChindrenMaster, 3400),
				asSuggestRecord("schuhe für  kinder", schuheForChindrenMaster, 3400),
				asSuggestRecord("schuhen fur kinde", schuheForChindrenMaster, 3400),
				asSuggestRecord("schuhen für  kinder", schuheForChindrenMaster, 3400),
				asSuggestRecord("schuhen für kinder", schuheForChindrenMaster, 3400),

				asSuggestRecord("damen sommerschuhe", damenSommerSchuheMaster, 3400),
				asSuggestRecord("damen somerschue", damenSommerSchuheMaster, 3400),
				asSuggestRecord("damen sommer shuhe", damenSommerSchuheMaster, 3400),

				asSuggestRecord("weiße schuhe", weißeSchuheMaster, 6600),
				asSuggestRecord("weise schuhe", weißeSchuheMaster, 6600),
				asSuggestRecord("weise schue", weißeSchuheMaster, 6600)));

		underTest.index(toIndex).join();

		List<QuerySuggester.Result> results = underTest.suggest("schuhe");

		assertThat(results).hasSize(1);
		final QuerySuggester.Result result = results.get(0);
		assertThat(result.getName()).isEqualTo(LuceneQuerySuggester.BEST_MATCHES_GROUP_NAME);
		final List<String> suggestions = result.getSuggestions();
		// not expecting "damenschuhe" since there is not a single variant that
		// starts with "schuhe"
		assertThat(suggestions).hasSize(3);
		assertThat(suggestions).element(0).isEqualTo(sportSchuheMaster);
		assertThat(suggestions).element(1).isEqualTo(weißeSchuheMaster);
		assertThat(suggestions).element(2).isEqualTo(schuheForChindrenMaster);
	}

	@DisplayName("Search for 'weisse s'")
	@Test
	void suggest4() {

		final String weißeSommerhoseMaster = "weiße sommerhose";
		final String weißeSchuheMaster = "weiße schuhe";
		final String weißeSneakerMaster = "weiße sneaker";
		final String weißeSockenMaster = "weiße socken";
		final String weißeSpitzenbluseMaster = "weiße spitzenbluse";
		final String weißeStoffhoseMaster = "weiße stoffhose";
		final String weisseStrickjackeMaster = "weisse strickjacke";
		final String weisseShortsMaster = "weisse shorts";
		final String weißesSpitzenkleidMaster = "weißes spitzenkleid";
		final String weißeTshirtsDamenMaster = "weiße tshirts damen";
		final String weißesSommerkleidMaster = "weißes sommerkleid";
		final String weissesHemdMaster = "weisses hemd";
		final String tShirtWeißMaster = "t shirt weiß";
		
		List<SuggestRecord> toIndex = new ArrayList<SuggestRecord>(asList(
				asSuggestRecord(weißeSommerhoseMaster, setOf("weisse sommerhose", "weisse sommerhosse"), 3400),
				asSuggestRecord(weißeSchuheMaster, setOf("weise schue", "weiße schuhe", "weise schuhe"), 6600),
				asSuggestRecord(weißeSneakerMaster, setOf("weise sneaker", "weise sneakers", "weisse sneaker", "weiße sneakers"), 0),
				asSuggestRecord(weißeSockenMaster,
						setOf("weise socken", "weisse socken.", "weissem socken", "weiße socke", "weißer socken", "weißes socken"),
						6600),
				asSuggestRecord(weißeSpitzenbluseMaster,
						setOf("weise spitzenbluse", "weisse spitzenbluse", "weiße spitzen blusen", "weiße spitzenblus"), 0),
				asSuggestRecord(weißeStoffhoseMaster, setOf("weiße stoffhosen", "weisse stoffhose", "weisse stoffhosen", "weiße stoff hosen"),
						1800),
				asSuggestRecord(weisseStrickjackeMaster,
						setOf("weisse strickjacke", "weise strickjacke", "weiße strickjacken", "weisestrickjacke", "weisse strick jacke",
								"weisse strickjacken", "weiße strickjacke.", "weißes strickjacke.", "weißestrickjacke"),
						9843),
				asSuggestRecord(weisseShortsMaster,
						setOf("weisse shorts", "weiße short", "weisse short", "weiße short,", "weißer shortst", "weise short,", "weiße shorts.",
								"weiße tshorts"),
						19408),
				asSuggestRecord(weißesSpitzenkleidMaster,
						setOf("weißes spitzen kleid", "weiss es spitzenkleid", "weisse spitzenkleid", "weisses spitzenkleid", "weißes  spitzenkleid"),
						1800),
				asSuggestRecord(weißeTshirtsDamenMaster,
						setOf("weiße t-shirts damen", "weisse shirts damen", "weiße t shirts damen", "weise shirts damen", "weise t shirts damen",
								"weisse t shirts damen", "weiße shirts damen", "weiße tshirts  damen"),
						6600),
				asSuggestRecord(weißesSommerkleidMaster,
						setOf("weißes sommerkleid", "weiße sommerkleider", "weise sommer kleid", "weises sommerkleid", "weiße sommer kleider",
								"weißes sommer kleid", "weisse sommerkleid", "weisses  sommerkleid", "weißes  sommerkleid", "weißes sommerkleider"),
						14599),
				asSuggestRecord(weissesHemdMaster,
						setOf("weisses hemd", "hemd weiss", "weiß hemd", "weiße hemd", "weiss es hemd", "weißes hemd", "weiss hemd", "weise hemd",
								"weisse hemd", "weisseshemd", "weiseß hemd", "weisser hemd", "weiße hemde", "hemd  weiss", "hemd, weiss",
								"hemd, weiß", "weise hemde", "weißehemd", "weißer hemd", "weißeshemd", "hemd  weiß", "hemd wei", "hemd weiss s",
								"hemd weisss", "hemd wweiss", "hemd,weiß", "hemdchen weiß", "we isses hemd", "weisehemd", "weisen hemd", "weishemd",
								"weiss  hemd", "weisse hemde", "weisse shemd", "weisses  hemd", "weisses <hemd", "weisße hemd", "weiße hemd s",
								"weiße hemdchen", "weißen hemd", "weißes hemd s", "weißes hemde", "weißhemd"),
						500418),
				asSuggestRecord(tShirtWeißMaster, setOf("weiße shirts", "weisse shirt", "weisse shirts"), 851928)

		));

		underTest.index(toIndex).join();

		List<QuerySuggester.Result> results = underTest.suggest("weisse s", 20, Collections.emptySet());

		assertThat(results).hasSizeGreaterThanOrEqualTo(2);
		QuerySuggester.Result result = results.get(0);
		assertThat(result.getName()).isEqualTo(LuceneQuerySuggester.BEST_MATCHES_GROUP_NAME);
		List<String> suggestions = result.getSuggestions();
		assertThat(suggestions).hasSize(8);
		assertThat(suggestions).element(0).isEqualTo(weisseShortsMaster);
		assertThat(suggestions).element(1).isEqualTo(weisseStrickjackeMaster);
		assertThat(suggestions).element(2).isEqualTo(weißeSockenMaster);
		assertThat(suggestions).element(3).isEqualTo(weißeSchuheMaster);
		assertThat(suggestions).element(4).isEqualTo(weißeSommerhoseMaster);
		assertThat(suggestions).element(5).isEqualTo(weißeStoffhoseMaster);
		assertThat(suggestions).element(6).isEqualTo(weißeSpitzenbluseMaster);
		assertThat(suggestions).element(7).isEqualTo(weißeSneakerMaster);

		result = results.get(1);
		suggestions = result.getSuggestions();
		assertThat(result.getName()).isEqualTo(LuceneQuerySuggester.TYPO_MATCHES_GROUP_NAME);
		assertThat(suggestions).element(0).isEqualTo(tShirtWeißMaster);
		assertThat(suggestions).element(1).isEqualTo(weissesHemdMaster);
		assertThat(suggestions).element(2).isEqualTo(weißesSommerkleidMaster);
		assertThat(suggestions).element(3).isEqualTo(weißeTshirtsDamenMaster);
		assertThat(suggestions).element(4).isEqualTo(weißesSpitzenkleidMaster);
	}

	@DisplayName("Ignore stopwords (like `früher`) for German locale")
	@Test
	void germanLocale(@TempDir Path indexFolder) throws IOException {
		underTest = new LuceneQuerySuggester(indexFolder, Locale.GERMAN, modifiedTermsService, getWordSet(Locale.GERMAN));

		final String sportSchuheMaster = "sport schuhe";
		final String schuheForKinderMaster = "schuhe für kinder";
		List<SuggestRecord> toIndex = new ArrayList<>(asList(
				asSuggestRecord("sport schuhe", sportSchuheMaster, 226000),
				asSuggestRecord("sportschue", sportSchuheMaster, 226000),
				asSuggestRecord("sport früher shuhe", sportSchuheMaster, 226000),
				asSuggestRecord("sport-schuhe", sportSchuheMaster, 226000),

				asSuggestRecord("schue früher kinder", schuheForKinderMaster, 3400),
				asSuggestRecord("schuh für kinder", schuheForKinderMaster, 3400),
				asSuggestRecord("schuhe  für kinder", schuheForKinderMaster, 3400)));

		underTest.index(toIndex).join();

		{
			// `für` is not a stopword so there are results for it
			List<QuerySuggester.Result> resultsForFür = underTest.suggest("für");
			assertThat(resultsForFür).hasSize(1);
			final QuerySuggester.Result result = resultsForFür.get(0);
			assertThat(result.getName()).isEqualTo(LuceneQuerySuggester.BEST_MATCHES_GROUP_NAME);
			final List<String> suggestions = result.getSuggestions();
			assertThat(suggestions).hasSize(1);
			assertThat(suggestions).element(0).isEqualTo(schuheForKinderMaster);
		}

		{
			// `früher` is a stopword so there are no results for it
			List<QuerySuggester.Result> resultsForFrüher = underTest.suggest("früher");
			assertThat(resultsForFrüher).hasSize(0);
		}
	}

	@DisplayName("Search for `hmd` should return fuzzy results scored by the suggestions' weight. The variants are ignored for fuzzy suggester")
	@Test
	void suggest_fuzzy_2(@TempDir Path indexFolder) throws IOException {
		underTest = new LuceneQuerySuggester(indexFolder, Locale.GERMAN, modifiedTermsService, getWordSet(Locale.GERMAN));

		final String hemdMaster = "hemd";
		final String hoseMaster = "hose";
		final String shirtMaster = "shirt";
		final String dressMaster = "dress";
		List<SuggestRecord> toIndex = new ArrayList<>(asList(
				asSuggestRecord("aaa", hemdMaster, 2),
				asSuggestRecord("bbb", hemdMaster, 2),
				asSuggestRecord("ccc", shirtMaster, 1),
				asSuggestRecord("ddd", dressMaster, 1),

				asSuggestRecord("eee", hoseMaster, 3),
				asSuggestRecord("fff", hoseMaster, 3),
				asSuggestRecord("ggg", hemdMaster, 2)));

		underTest.index(toIndex).join();

		List<QuerySuggester.Result> resultsForHmd = underTest.suggest("hmd");

		assertThat(resultsForHmd).hasSize(2);
		{
			final QuerySuggester.Result fuzzyOneEditResult = resultsForHmd.get(0);
			assertThat(fuzzyOneEditResult.getName()).isEqualTo(LuceneQuerySuggester.FUZZY_MATCHES_ONE_EDIT_GROUP_NAME);
			final List<String> suggestions = fuzzyOneEditResult.getSuggestions();
			assertThat(suggestions).hasSize(1);
			assertThat(suggestions).element(0).isEqualTo(hemdMaster);
		}
		{
			final QuerySuggester.Result fuzzyTwoEditsResult = resultsForHmd.get(1);
			assertThat(fuzzyTwoEditsResult.getName()).isEqualTo(LuceneQuerySuggester.FUZZY_MATCHES_TWO_EDITS_GROUP_NAME);
			final List<String> suggestions = fuzzyTwoEditsResult.getSuggestions();
			assertThat(suggestions).hasSize(2);
			// `hose` is found with fuzziness=2
			assertThat(suggestions).element(0).isEqualTo(dressMaster);
			// `dress` is found with fuzziness=2 because of DFA:
			// http://julesjacobs.github.io/2015/06/17/disqus-levenshtein-simple-and-fast.html
			// i.e. it removes `hm` and then `d` matches as a prefix of `dress`
			assertThat(suggestions).element(1).isEqualTo(hoseMaster);
		}
	}

	@DisplayName("Search for `fleece` should return sharpened results")
	@Test
	void suggest_relaxed_sharpened(@TempDir Path indexFolder) throws IOException {
		List<String> sharpenedQueries = java.util.Arrays.asList(
				"fleecejacke", "fleece jacke", "fleeceweste", "fleece overall", "fleece weste", 
				"fleecepullover", "fleece baby", "fleece halswaermer", "fleece pullover", "fleecehose");
		underTest = new LuceneQuerySuggester(indexFolder, Locale.GERMAN,
				new ModifiedTermsService(emptyMap(), singletonMap("fleece", sharpenedQueries)),
				getWordSet(Locale.GERMAN));

		// nothing is indexed

		List<QuerySuggester.Result> results = underTest.suggest("fleece");

		assertThat(results).hasSize(1);
		final QuerySuggester.Result result = results.get(0);
		assertThat(result.getName()).isEqualTo(LuceneQuerySuggester.SHARPENED_GROUP_NAME);
		final List<String> suggestions = result.getSuggestions();
		assertThat(suggestions).hasSize(10);
		assertThat(suggestions).containsExactly(sharpenedQueries.toArray(new String[0]));
	}

	@DisplayName("Search for `fleeceanzug` should return relaxed results")
	@Test
	void suggest_relaxed_relaxed(@TempDir Path indexFolder) throws IOException {
		underTest = new LuceneQuerySuggester(indexFolder, Locale.GERMAN,
				new ModifiedTermsService(singletonMap("fleeceanzug", singletonList("fleece")), emptyMap()),
				getWordSet(Locale.GERMAN));

		// nothing is indexed

		List<QuerySuggester.Result> results = underTest.suggest("fleeceanzug");

		assertThat(results).hasSize(1);
		final QuerySuggester.Result result = results.get(0);
		assertThat(result.getName()).isEqualTo(LuceneQuerySuggester.RELAXED_GROUP_NAME);
		final List<String> suggestions = result.getSuggestions();
		assertThat(suggestions).hasSize(1);
		assertThat(suggestions).containsExactly("fleece");
	}

	@DisplayName("Destroyed Suggester returns empty result")
	@Test
	void destroyedSuggesterReturnsEmptyResult() throws Exception {
		List<SuggestRecord> suggestions = new ArrayList<>();
		suggestions.add(asSuggestRecord("fnord", "", 1));
		underTest.index(suggestions).join();

		assertThat(underTest.suggest("fno")).hasSize(1);

		underTest.destroy();

		assertThat(underTest.suggest("fno")).isEmpty();
	}



	private SuggestRecord asSuggestRecord(String bestQuery, Set<String> searchData, int weight) {
		return new SuggestRecord(bestQuery, bestQuery, StringUtils.join(searchData, " "), emptySet(), weight);
	}
	
	private SuggestRecord asSuggestRecord(String searchString, String bestQuery, int weight) {
		return new SuggestRecord(bestQuery, bestQuery, searchString, emptySet(), weight);
	}

	private SuggestRecord asSuggestRecord(String searchString, String bestQuery, int weight, Set<String> tags) {
		return new SuggestRecord(bestQuery, bestQuery, searchString, tags, weight);
	}

	private Set<String> setOf(String... entries) {
		Set<String> result = new HashSet<>(asList(entries));
		return result;
	}

	private CharArraySet getWordSet(Locale locale) throws IOException {
		if (locale != null) {
			final String languageCode = locale.getLanguage();
			String stopWordsFileName = null;
			switch (languageCode) {
				case "de":
					stopWordsFileName = "de.txt";
					break;
				default:
					log.info("No stopwords file for locale '{}'", locale);
			}

			if (stopWordsFileName != null) {
				final InputStream stopWordInputStream = LuceneQuerySuggester.class.getResourceAsStream("/stopwords/" + stopWordsFileName);
				final CharArraySet stopWordSet = WordlistLoader.getWordSet(new InputStreamReader(stopWordInputStream));
				return stopWordSet;
			}
		}

		return null;
	}

}
