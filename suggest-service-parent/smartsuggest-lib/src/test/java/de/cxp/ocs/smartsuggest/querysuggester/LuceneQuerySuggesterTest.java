package de.cxp.ocs.smartsuggest.querysuggester;

import static de.cxp.ocs.smartsuggest.querysuggester.lucene.LuceneQuerySuggester.BEST_MATCHES_GROUP_NAME;
import static de.cxp.ocs.smartsuggest.querysuggester.lucene.LuceneQuerySuggester.FUZZY_MATCHES_ONE_EDIT_GROUP_NAME;
import static de.cxp.ocs.smartsuggest.querysuggester.lucene.LuceneQuerySuggester.FUZZY_MATCHES_TWO_EDITS_GROUP_NAME;
import static de.cxp.ocs.smartsuggest.querysuggester.lucene.LuceneQuerySuggester.PAYLOAD_GROUPMATCH_KEY;
import static de.cxp.ocs.smartsuggest.querysuggester.lucene.LuceneQuerySuggester.RELAXED_GROUP_NAME;
import static de.cxp.ocs.smartsuggest.querysuggester.lucene.LuceneQuerySuggester.SHINGLE_MATCHES_GROUP_NAME;
import static de.cxp.ocs.smartsuggest.querysuggester.lucene.LuceneQuerySuggester.TYPO_MATCHES_GROUP_NAME;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.WordlistLoader;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import de.cxp.ocs.smartsuggest.querysuggester.lucene.LuceneQuerySuggester;
import de.cxp.ocs.smartsuggest.querysuggester.modified.ModifiedTermsService;
import de.cxp.ocs.smartsuggest.spi.SuggestConfig;
import de.cxp.ocs.smartsuggest.spi.SuggestConfig.SortStrategy;
import de.cxp.ocs.smartsuggest.spi.SuggestRecord;
import lombok.extern.slf4j.Slf4j;

@SuppressWarnings("deprecation")
@Slf4j
class LuceneQuerySuggesterTest {

	private LuceneQuerySuggester	underTest;
	private ModifiedTermsService	modifiedTermsService	= mock(ModifiedTermsService.class);
	private SuggestConfig			suggestConfig;

	@BeforeEach
	void beforeEach(@TempDir Path indexFolder) throws IOException {
		suggestConfig = new SuggestConfig();
		suggestConfig.setAlwaysDoFuzzy(true);
		suggestConfig.setLocale(Locale.GERMAN);
		suggestConfig.setSortOrder(SortStrategy.MatchGroupsSeparated);
		underTest = new LuceneQuerySuggester(indexFolder, suggestConfig, modifiedTermsService, getWordSet(Locale.ROOT));
	}

	@AfterEach
	void close() throws Exception {
		if (underTest != null)
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

		List<Suggestion> results = underTest.suggest("user");

		assertThat(results).hasSize(2);
		assertGroupName(results.get(0), TYPO_MATCHES_GROUP_NAME);
		assertLabel(results.get(0), masterQuery2);
		assertLabel(results.get(1), masterQuery1);
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

		List<Suggestion> results = underTest.suggest("user");

		assertThat(results).hasSize(1);
		assertGroupName(results.get(0), TYPO_MATCHES_GROUP_NAME);
		assertLabel(results.get(0), masterQuery1);
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

		List<Suggestion> results = underTest.suggest("u");

		assertThat(results).hasSize(4);
		assertGroupName(results.get(0), TYPO_MATCHES_GROUP_NAME);
		assertLabel(results.get(0), masterQuery1);
		assertLabel(results.get(1), masterQuery3);
		assertLabel(results.get(2), masterQuery2);
		assertLabel(results.get(3), masterQuery4);
	}

	@DisplayName("Search for 'guci' should return properly spelled 'Gucci'")
	@Test
	void suggest_fuzzy_0() {
		final String Gucci = "Gucci";
		List<SuggestRecord> toIndex = new ArrayList<>(singletonList(
				asSuggestRecord("gucci", Gucci, 1000)));

		underTest.index(toIndex).join();

		List<Suggestion> results = underTest.suggest("guci");
		assertThat(results).hasSize(1);
		assertSuggestion(results.get(0), Gucci, FUZZY_MATCHES_ONE_EDIT_GROUP_NAME);

	}

	@DisplayName("Search for 'veshin' should return properly spelled 'Washington'")
	@Test
	void suggest_fuzzy_1() {
		final String Washington = "washington";
		List<SuggestRecord> toIndex = new ArrayList<>(singletonList(
				asSuggestRecord("washington", Washington, 1000)));

		underTest.index(toIndex).join();

		List<Suggestion> results = underTest.suggest("veshin");
		assertThat(results).hasSize(1);
		assertSuggestion(results.get(0), Washington, FUZZY_MATCHES_TWO_EDITS_GROUP_NAME);
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

		List<Suggestion> results = underTest.suggest("shirt");
		assertThat(results).hasSize(2);
		assertSuggestion(results.get(0), mensShirts, BEST_MATCHES_GROUP_NAME);
		assertSuggestion(results.get(1), womensShirts, BEST_MATCHES_GROUP_NAME);
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
		List<Suggestion> results = underTest.suggest("men's");
		assertThat(results).hasSize(3);
		assertSuggestion(results.get(0), mensShirts, BEST_MATCHES_GROUP_NAME);
		assertSuggestion(results.get(1), withoutSMensShirts, FUZZY_MATCHES_ONE_EDIT_GROUP_NAME);
		assertSuggestion(results.get(2), womensShirts, FUZZY_MATCHES_TWO_EDITS_GROUP_NAME);
		
		// no apostrophe
		results = underTest.suggest("mens");
		assertThat(results).hasSize(2);
		assertSuggestion(results.get(0), withoutSMensShirts, BEST_MATCHES_GROUP_NAME);
		assertSuggestion(results.get(1), mensShirts, FUZZY_MATCHES_ONE_EDIT_GROUP_NAME);

		// no space
		results = underTest.suggest("men'sshi");
		assertThat(results).hasSize(2);
		assertSuggestion(results.get(0), mensShirts, FUZZY_MATCHES_ONE_EDIT_GROUP_NAME);
		assertSuggestion(results.get(1), withoutSMensShirts, FUZZY_MATCHES_TWO_EDITS_GROUP_NAME);
		// women's shirts is not in the results because FuzzySuggester supports
		// at most 2 edits
	}

	@Disabled("Lucene does not sort the way we want. More logic should be added in LuceneQuerySuggester.suggest")
	@DisplayName("Search for `new` should return first the phrases containing the word `new` and then words containing `new`")
	@Test
	void suggest_whole_word_first() {
		final String NewYork = "new york";
		final String Newton = "newton";
		final String Renew = "renew";
		List<SuggestRecord> toIndex = new ArrayList<>(asList(
				asSuggestRecord(NewYork, NewYork, 100),
				asSuggestRecord(Renew, Renew, 100),
				asSuggestRecord(Newton, Newton, 101)));

		underTest.index(toIndex).join();

		List<Suggestion> results = underTest.suggest("new");
		assertSuggestion(results.get(0), NewYork, BEST_MATCHES_GROUP_NAME);
		assertSuggestion(results.get(1), Newton, BEST_MATCHES_GROUP_NAME);
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
				asSuggestRecord("name 3", Book1, 102, setOf("book"))));

		underTest.index(toIndex).join();

		List<Suggestion> bookResults = underTest.suggest("name", 10, setOf("book"));
		assertSuggestion(bookResults.get(0), Book1, TYPO_MATCHES_GROUP_NAME);

		List<Suggestion> movieResults = underTest.suggest("name", 10, setOf("movie"));
		assertSuggestion(movieResults.get(0), Movie1, TYPO_MATCHES_GROUP_NAME);
		assertSuggestion(movieResults.get(1), Movie2, TYPO_MATCHES_GROUP_NAME);

		List<Suggestion> bothResults = underTest.suggest("name", 10, setOf("book", "movie"));
		assertLabel(bothResults.get(0), Book1);
		assertLabel(bothResults.get(1), Movie1);
		assertLabel(bothResults.get(2), Movie2);
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

		List<Suggestion> results = underTest.suggest("beige men's shirts");
		assertSuggestion(results.get(0), BeigeMensShirts, BEST_MATCHES_GROUP_NAME);

		// expect results in the order of the assigned weights
		assertSuggestion(results.get(1), Shirts, SHINGLE_MATCHES_GROUP_NAME);
		assertSuggestion(results.get(2), WorkShirts, SHINGLE_MATCHES_GROUP_NAME);
		assertSuggestion(results.get(3), WomensShirts, SHINGLE_MATCHES_GROUP_NAME);
		assertSuggestion(results.get(4), MensShirts, SHINGLE_MATCHES_GROUP_NAME);

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

		List<Suggestion> results = underTest.suggest("men's shi");

		assertSuggestion(results.get(0), MensShirts, BEST_MATCHES_GROUP_NAME);
		assertSuggestion(results.get(1), BeigeMensShirts, BEST_MATCHES_GROUP_NAME);

		assertSuggestion(results.get(2), WomensShirts, FUZZY_MATCHES_TWO_EDITS_GROUP_NAME);
				
		assertSuggestion(results.get(3), ShirtsMen, SHINGLE_MATCHES_GROUP_NAME);
		assertSuggestion(results.get(4), Shirts, SHINGLE_MATCHES_GROUP_NAME);
		assertSuggestion(results.get(5), WorkShirts, SHINGLE_MATCHES_GROUP_NAME);

		List<Suggestion> results2 = underTest.suggest("shirts men");
		assertSuggestion(results2.get(0), ShirtsMen, BEST_MATCHES_GROUP_NAME);
		assertSuggestion(results2.get(1), MensShirts, BEST_MATCHES_GROUP_NAME);
		assertSuggestion(results2.get(2), BeigeMensShirts, BEST_MATCHES_GROUP_NAME);
				
		assertSuggestion(results2.get(3), Shirts, SHINGLE_MATCHES_GROUP_NAME);
		assertSuggestion(results2.get(4), WorkShirts, SHINGLE_MATCHES_GROUP_NAME);
		assertSuggestion(results2.get(5), WomensShirts, SHINGLE_MATCHES_GROUP_NAME);
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

		List<Suggestion> results = underTest.suggest("schuhe");

		assertThat(results).hasSize(3);
		final Suggestion result = results.get(0);
		assertGroupName(result, BEST_MATCHES_GROUP_NAME);
		// not expecting "damenschuhe" since there is not a single variant that
		// starts with "schuhe"
		assertThat(results).hasSize(3);
		assertLabel(results.get(0), sportSchuheMaster);
		assertLabel(results.get(1), weißeSchuheMaster);
		assertLabel(results.get(2), schuheForChindrenMaster);
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

		List<Suggestion> results = underTest.suggest("weisse s", 20, Collections.emptySet());

		assertThat(results).hasSizeGreaterThanOrEqualTo(2);
		assertThat(results).hasSize(13);

		assertGroupName(results.get(0), LuceneQuerySuggester.BEST_MATCHES_GROUP_NAME);
		assertGroupName(results.get(7), LuceneQuerySuggester.BEST_MATCHES_GROUP_NAME);
		assertLabel(results.get(0), weisseShortsMaster);
		assertLabel(results.get(1), weisseStrickjackeMaster);
		assertLabel(results.get(2), weißeSockenMaster);
		assertLabel(results.get(3), weißeSchuheMaster);
		assertLabel(results.get(4), weißeSommerhoseMaster);
		assertLabel(results.get(5), weißeStoffhoseMaster);
		assertLabel(results.get(6), weißeSpitzenbluseMaster);
		assertLabel(results.get(7), weißeSneakerMaster);

		assertGroupName(results.get(8), LuceneQuerySuggester.TYPO_MATCHES_GROUP_NAME);
		assertGroupName(results.get(12), LuceneQuerySuggester.TYPO_MATCHES_GROUP_NAME);
		assertLabel(results.get(8), tShirtWeißMaster);
		assertLabel(results.get(9), weissesHemdMaster);
		assertLabel(results.get(10), weißesSommerkleidMaster);
		assertLabel(results.get(11), weißeTshirtsDamenMaster);
		assertLabel(results.get(12), weißesSpitzenkleidMaster);
	}

	@DisplayName("Ignore stopwords (like `früher`) for German locale")
	@Test
	void germanLocale(@TempDir Path indexFolder) throws IOException {
		underTest = new LuceneQuerySuggester(indexFolder, suggestConfig, modifiedTermsService, getWordSet(Locale.GERMAN));

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

		// `für` is not a stopword so there are results for it
		List<Suggestion> results = underTest.suggest("für");
		assertThat(results).hasSize(1);
		assertGroupName(results.get(0), BEST_MATCHES_GROUP_NAME);
		assertLabel(results.get(0), schuheForKinderMaster);

		{
			// `früher` is a stopword so there are no results for it
			List<Suggestion> resultsForStopword = underTest.suggest("früher");
			assertThat(resultsForStopword).hasSize(0);
		}
	}

	@DisplayName("Search for `hmd` should return fuzzy results scored by the suggestions' weight. The variants are ignored for fuzzy suggester")
	@Test
	void suggest_fuzzy_2(@TempDir Path indexFolder) throws IOException {
		underTest = new LuceneQuerySuggester(indexFolder, suggestConfig, modifiedTermsService, getWordSet(Locale.GERMAN));

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

		List<Suggestion> results = underTest.suggest("hmd");

		assertThat(results).hasSize(3);
		assertSuggestion(results.get(0), hemdMaster, FUZZY_MATCHES_ONE_EDIT_GROUP_NAME);

		// `hose` is found with fuzziness=2 and preferred because of higher
		// weight
		assertSuggestion(results.get(1), hoseMaster, FUZZY_MATCHES_TWO_EDITS_GROUP_NAME);

		// `dress` is found with fuzziness=2 because of DFA:
		// http://julesjacobs.github.io/2015/06/17/disqus-levenshtein-simple-and-fast.html
		// i.e. it removes `hm` and then `d` matches as a prefix of `dress`
		assertSuggestion(results.get(2), dressMaster, FUZZY_MATCHES_TWO_EDITS_GROUP_NAME);
	}

	@DisplayName("Search for `fleece` should return sharpened results")
	@Test
	void suggest_relaxed_sharpened(@TempDir Path indexFolder) throws IOException {
		List<String> sharpenedQueries = java.util.Arrays.asList(
				"fleecejacke", "fleece jacke", "fleeceweste", "fleece overall", "fleece weste", 
				"fleecepullover", "fleece baby", "fleece halswaermer", "fleece pullover", "fleecehose");

		underTest = new LuceneQuerySuggester(indexFolder, suggestConfig,
				new ModifiedTermsService(emptyMap(), singletonMap("fleece", sharpenedQueries), suggestConfig),
				getWordSet(Locale.GERMAN));

		// nothing is indexed

		{
			suggestConfig.setMaxSharpenedQueries(3);
			List<Suggestion> results = underTest.suggest("fleece");
			assertThat(results).hasSize(suggestConfig.getMaxSharpenedQueries());
		}

		suggestConfig.setMaxSharpenedQueries(100);
		List<Suggestion> results = underTest.suggest("fleece");
		Set<String> expectedLabels = new HashSet<>(sharpenedQueries);
		assertThat(results).hasSize(expectedLabels.size());

		for (Suggestion result : results) {
			assertTrue(expectedLabels.remove(result.getLabel()), "label '" + result.getLabel() + "' found, but was not expected");
		}
		assertTrue(expectedLabels.isEmpty(), "Some expected labels were not found: " + expectedLabels.toString());
	}

	@DisplayName("Search for `fleeceanzug` should return relaxed results")
	@Test
	void suggest_relaxed_relaxed(@TempDir Path indexFolder) throws IOException {
		underTest = new LuceneQuerySuggester(indexFolder, suggestConfig,
				new ModifiedTermsService(singletonMap("fleeceanzug", singletonList("fleece")), emptyMap(), new SuggestConfig()),
				getWordSet(Locale.GERMAN));

		// nothing is indexed

		List<Suggestion> results = underTest.suggest("fleeceanzug");
		assertThat(results).hasSize(1);
		assertSuggestion(results.get(0), "fleece", RELAXED_GROUP_NAME);
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

	@DisplayName("If the proerpty 'doReorderSecondaryMatches=true' is set, primary and secondary matches should be reordered according to their weight")
	@Test
	void suggest_reorder_secondary_matches(@TempDir Path indexFolder) throws IOException {
		suggestConfig.setSortOrder(SortStrategy.PrimaryAndSecondaryByWeight);
		try {
			underTest = new LuceneQuerySuggester(indexFolder, suggestConfig, modifiedTermsService, getWordSet(Locale.GERMAN));

			// will search for "sh"
			final String primary1 = "shirt"; // <<sh matches primary
			final String primary2 = "socks";
			final String primary3 = "shoes";// <<sh matches primary
			List<SuggestRecord> toIndex = new ArrayList<>(asList(
					asSuggestRecord("super nice stuff", primary1, 10),
					asSuggestRecord("shiney", primary2, 20), // <<sh matches secondary
					asSuggestRecord("sustainable", primary3, 30)));

			underTest.index(toIndex).join();

			List<Suggestion> results = underTest.suggest("sh");
			System.out.println(results);
			assertThat(results).hasSize(3);
			assertLabel(results.get(0), primary3);
			assertLabel(results.get(1), primary2);
			assertLabel(results.get(2), primary1);

			assertSuggestion(results.get(1), primary2, TYPO_MATCHES_GROUP_NAME);
		}
		finally {
			System.clearProperty("doReorderSecondaryMatches");
		}
	}

	private void assertLabel(Suggestion suggestion, String expectedLabel) {
		assertThat(suggestion.getLabel()).isEqualTo(expectedLabel);
	}

	private void assertGroupName(Suggestion suggestion, String expectedGroupName) {
		assertThat(suggestion.getPayload().get(LuceneQuerySuggester.PAYLOAD_GROUPMATCH_KEY)).isEqualTo(expectedGroupName);
	}

	private void assertSuggestion(Suggestion suggestion, String expectedLabel, String expectedGroupName) {
		assertLabel(suggestion, expectedLabel);
		assertGroupName(suggestion, expectedGroupName);
	}

	private SuggestRecord asSuggestRecord(String bestQuery, Set<String> searchData, int weight) {
		return new SuggestRecord(bestQuery, StringUtils.join(searchData, " "), singletonMap("label", bestQuery), emptySet(), weight);
	}
	
	private SuggestRecord asSuggestRecord(String searchString, String bestQuery, int weight) {
		return new SuggestRecord(bestQuery, searchString, singletonMap("label", bestQuery), emptySet(), weight);
	}

	private SuggestRecord asSuggestRecord(String searchString, String bestQuery, int weight, Set<String> tags) {
		return new SuggestRecord(bestQuery, searchString, singletonMap("label", bestQuery), tags, weight);
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

	/**
	 * minimal suggestion that only compares label and groupname
	 *
	 */
	static class AssertSuggestion extends Suggestion {

		AssertSuggestion(String label, String groupName) {
			super(label);
			this.setPayload(Collections.singletonMap(PAYLOAD_GROUPMATCH_KEY, groupName));
		}

		@Override
		public boolean equals(Object obj) {
			System.out.println("using equals");
			if (!(obj instanceof Suggestion)) return false;
			Suggestion other = (Suggestion)obj;
			return this.getLabel().equals(other.getLabel())
					&& this.getPayload().get(PAYLOAD_GROUPMATCH_KEY).equals(other.getPayload().get(PAYLOAD_GROUPMATCH_KEY));
		}

		@Override
		public int hashCode() {
			System.out.println("using hashcode");
			return super.hashCode();
		}
	}

}
