package net.dean.jraw.test;

import net.dean.jraw.Endpoint;
import net.dean.jraw.Version;
import net.dean.jraw.http.NetworkException;
import net.dean.jraw.models.Flair;
import net.dean.jraw.models.JsonInteraction;
import net.dean.jraw.models.JsonModel;
import net.dean.jraw.models.RenderStringPair;
import net.dean.jraw.models.Listing;
import net.dean.jraw.models.More;
import net.dean.jraw.models.Submission;
import net.dean.jraw.pagination.SubredditPaginator;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.testng.Assert.*;

public class InternalsTest extends RedditTest {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testModifyListing() {
        Listing<Submission> submissions = new SubredditPaginator(reddit).next();
        submissions.remove(0);
    }

    @Test
    public void testJsonModelToString() {
        MockJsonModel model = new MockJsonModel();
        String actual = model.toString();
        String expected = "MockJsonModel {getBar()=\"0.0\", getFoo()=\"foo\", getSomeModel()=[MockJsonModel], " +
                "throwsException()=[threw java.lang.UnsupportedOperationException: exception message]}";
        assertEquals(actual, expected);
    }

    @Test
    public void testJsonModelAsString() {
        MockJsonModel model = new MockJsonModel();
        assertEquals(model.asString(null), "null");
        assertEquals(model.asString("hello"), "hello");
    }

    @Test
    public void testModifyListingData() {
        final Listing<Submission> listing = new SubredditPaginator(reddit).next();
        List<CodeBlock> codeBlocks = new ArrayList<>();

        // List of CodeBlocks that will modify the listing
        codeBlocks.addAll(Arrays.asList(
                () -> listing.add(null),
                () -> listing.add(0, null),
                () -> listing.addAll(0, null),
                () -> listing.addAll(null),
                listing::clear,
                () -> listing.remove(null),
                () -> listing.remove(0),
                () -> listing.removeAll(null),
                () -> listing.retainAll(null),
                () -> listing.set(0, null)
        ));

        for (CodeBlock e : codeBlocks) {
            assertNotNull(getException(e));
        }
    }

    @Test
    public void testRenderStringToString() {
        RenderStringPair string = new RenderStringPair("md", "html");
        assertEquals(string.md(), string.toString());
    }

    @Test
    public void testFlair() {
        String css = "css";
        String text = "text";
        Flair f = new Flair(css, text);
        assertEquals(f.getCssClass(), css);
        assertEquals(f.getText(), text);

        Flair f2 = new Flair(css, text);
        basicObjectTest(f, f2);
    }

    @Test
    public void testNetworkException() {
        NetworkException ex = new NetworkException(404);
        assertEquals(ex.getCode(), 404);
        assertEquals(ex.getMessage(), "Request returned bad code (404)");

        ex = new NetworkException("message");
        assertTrue(ex.getCode() == -1);

        NullPointerException cause = new NullPointerException();
        ex = new NetworkException("message", cause);
        assertEquals(ex.getCause(), cause);
    }

    @Test
    public void testVersion() {
        MockVersion v = new MockVersion(1, 2, 3);
        assertTrue(v.getMajor() == 1);
        assertTrue(v.getMinor() == 2);
        assertTrue(v.getPatch() == 3);
        assertFalse(v.isSnapshot());

        MockVersion v2 = new MockVersion(1, 2, 3);
        basicObjectTest(v, v2);
    }

    /**
     * Makes sure that equals(), hashCode(), and toString() were overridden
     *
     * @param o1 An object
     * @param o2 An object which is supposedly equal to o1
     * @param <T> The type of object
     */
    private <T> void basicObjectTest(T o1, T o2) {
        // Make sure the default toString() was overridden
        assertFalse(o1.toString().equals(o1.getClass().getName() + "@" + Integer.toHexString(o1.hashCode())),
                "toString() not overridden");
        assertEquals(o1.hashCode(), o2.hashCode(), "hashCode() not overridden");
        assertEquals(o1, o2, "equals() not overridden");
    }

    private class MockVersion extends Version {

        protected MockVersion(int major, int minor, int patch) {
            super(major, minor, patch);
        }
    }

    @Test
    public void testMore() {
        try {
            String json = "{" +
                    "\"count\":20," +
                    "\"parent_id\":\"t1_ckuxak4\"," +
                    "\"children\":[" +
                    "    \"ckuzahs\"," +
                    "    \"ckuzll8\"," +
                    "    \"ckuzt7k\"," +
                    "    \"ckv4o7l\"," +
                    "    \"ckv5qjp\"" +
                    "]," +
                    "\"id\":\"ckuzahs\"," +
                    "\"name\":\"t1_ckuzahs\"" +
                    "}";
            JsonNode dataNode = objectMapper.readTree(json);

            More more = new More(dataNode);

            assertTrue(more.getCount() == 20);

            List<String> expectedChildren = Arrays.asList("ckuzahs", "ckuzll8", "ckuzt7k", "ckv4o7l", "ckv5qjp");
            assertEquals(more.getChildrenIds(), expectedChildren);
        } catch (IOException e) {
            handle(e);
        }
    }

    private Exception getException(CodeBlock e) {
        try {
            e.execute();
            return null;
        } catch (Exception ex) {
            return ex;
        }
    }

    @Test
    public void testEndpoint() {
        String requestDescriptor = "GET /api/{foo}/{bar}/baz";
        String category = "cat";

        Endpoint e = new Endpoint(requestDescriptor, category);
        assertEquals(e.getCategory(), "cat");
        assertEquals(e.getUri(), "/api/{foo}/{bar}/baz");
        assertEquals(e.getVerb(), "GET");
        assertEquals(e.getUrlParams(), Arrays.asList("{foo}", "{bar}"));
        assertEquals(e.getRequestDescriptor(), requestDescriptor);

        Endpoint e2 = new Endpoint(requestDescriptor, category);
        basicObjectTest(e, e2);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testEndpointInvalidRequestDescriptor() {
        new Endpoint("part1 part2 part3");
    }

    private interface CodeBlock {
        public void execute();
    }

    // Normally this class would be private but JsonModel.toString() will throw an IllegalAccessException because it
    // can't access this class' @JsonInteraction methods
    public class MockJsonModel extends JsonModel {

        public MockJsonModel() {
            super(null);
        }

        @JsonInteraction
        public String getFoo() {
            return "foo";
        }

        @JsonInteraction
        public Float getBar() {
            return 0f;
        }

        @JsonInteraction
        public String throwsException() {
            throw new UnsupportedOperationException("exception message");
        }

        @JsonInteraction
        public JsonModel getSomeModel() {
            return new MockJsonModel();
        }

        // Override to raise privacy level from protected to public
        @Override
        public String asString(Object val) {
            return super.asString(val);
        }
    }
}
