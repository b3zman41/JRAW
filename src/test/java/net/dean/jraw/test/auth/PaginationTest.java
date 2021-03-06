package net.dean.jraw.test.auth;

import net.dean.jraw.ApiException;
import net.dean.jraw.JrawUtils;
import net.dean.jraw.managers.MultiRedditManager;
import net.dean.jraw.http.NetworkException;
import net.dean.jraw.models.MultiReddit;
import net.dean.jraw.models.Listing;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.Thing;
import net.dean.jraw.pagination.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.testng.Assert.assertTrue;

/**
 * This class tests all concrete subclasses of {@link net.dean.jraw.pagination.Paginator}
 */
public class PaginationTest extends AuthenticatedRedditTest {
    private MultiRedditManager manager = new MultiRedditManager(reddit);

    @Test
    public void testSubredditPaginatorFrontPage() throws NetworkException {
        SubredditPaginator frontPage = new SubredditPaginator(reddit);
        commonTest(frontPage);
    }

    @Test
    public void testSubredditPaginatorSubreddit() throws NetworkException {
        SubredditPaginator pics = new SubredditPaginator(reddit, "pics");
        commonTest(pics);
    }

    @Test
    public void testPaginatorTimePeriod() {
        final long millisecondsInAnHour = 60 * 60 * 1000;

        SubredditPaginator frontPage = new SubredditPaginator(reddit);
        frontPage.setTimePeriod(TimePeriod.HOUR);
        Listing<Submission> submissions = frontPage.next();

        for (Submission post : submissions) {
            long epochPosted = post.getCreatedUtc().toInstant().getEpochSecond();
            long epochNow = new Date().toInstant().getEpochSecond();

            // Make sure the submissions have been posted in the past hour
            assertTrue(epochPosted > epochNow - millisecondsInAnHour);
        }
    }

    @Test
    public void testSearchPaginator() throws NetworkException {
        SearchPaginator paginator = new SearchPaginator(reddit, "test");
        String subreddit = "AskReddit";
        paginator.setSubreddit(subreddit);
        commonTest(paginator);

        for (Submission s : paginator.getCurrentListing()) {
            // Make sure they all come from the same subreddit
            Assert.assertTrue(s.getSubredditName().equals(subreddit));
        }
    }

    @Test
    public void testSpecificPaginator() throws NetworkException {
        // It would be easier to declare fullNames as an array, but we want to use List.contains()
        List<String> fullNames = Arrays.asList("t3_92dd8", "t3_290287", "t3_28zy98", "t3_28zh9i");
        SpecificPaginator paginator = new SpecificPaginator(reddit, fullNames.toArray(new String[fullNames.size()]));

        Listing<Submission> submissions = paginator.next();
        for (Submission s : submissions) {
            Assert.assertTrue(fullNames.contains(s.getFullName()));
        }
    }

    @Test
    public void testUserContributionPaginator() throws NetworkException {
        // Test all Where values
        for (UserContributionPaginator.Where where : UserContributionPaginator.Where.values()) {
            UserContributionPaginator paginator = new UserContributionPaginator(reddit, where, reddit.getAuthenticatedUser());
            commonTest(paginator);
        }
    }

    @Test
    public void testUserSubredditsPaginator() throws NetworkException {
        // Test all Where values
        for (UserSubredditsPaginator.Where where : UserSubredditsPaginator.Where.values()) {
            UserSubredditsPaginator paginator = new UserSubredditsPaginator(reddit, where);
            commonTest(paginator);
        }
    }

    @Test
    public void testAllSubredditsPaginator() throws NetworkException {
        // Test all Where values
        for (AllSubredditsPaginator.Where where : AllSubredditsPaginator.Where.values()) {
            AllSubredditsPaginator paginator = new AllSubredditsPaginator(reddit, where);
            commonTest(paginator);
        }
    }

    @Test
    public void testMultiRedditPaginator() throws NetworkException, ApiException {
        MultiReddit multi = manager.mine().get(0);

        MultiRedditPaginator paginator = new MultiRedditPaginator(reddit, multi);
        commonTest(paginator);
    }

    @Test
    public void testCompoundSubredditPaginator() throws NetworkException {
        CompoundSubredditPaginator paginator = new CompoundSubredditPaginator(reddit, Arrays.asList("programming", "java"));
        commonTest(paginator);
    }

    @Test
    public void testMultiHubPaginator() throws NetworkException, ApiException {
        MultiHubPaginator paginator = new MultiHubPaginator(reddit);

        final int testLimit = 3;
        Listing<MultiHubPaginator.MultiRedditId> ids = paginator.next();

        for (int i = 0; i <= testLimit; i++) {
            MultiHubPaginator.MultiRedditId id = ids.get(i);
            MultiReddit multi = manager.get(id.getOwner(), id.getName());
            validateModel(multi);
        }
    }

    @Test
    public void testInboxPaginator() {
        for (InboxPaginator.Where where : InboxPaginator.Where.values()) {
            InboxPaginator paginator = new InboxPaginator(reddit, where);
            commonTest(paginator);
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testChangeRequestParamters() {
        AllSubredditsPaginator paginator = new AllSubredditsPaginator(reddit, AllSubredditsPaginator.Where.NEW);
        paginator.next();
        // Modifying the request parameters after the initial request, without calling reset
        paginator.setLimit(Paginator.DEFAULT_LIMIT);
        // Should throw an IllegalStateException
        paginator.next();
    }

    @Test
    public void testResetRequestParameters() {
        AllSubredditsPaginator paginator = new AllSubredditsPaginator(reddit, AllSubredditsPaginator.Where.NEW);
        paginator.next();
        paginator.setLimit(Paginator.DEFAULT_LIMIT);
        // We know it has already started, but just making sure this method works as expected
        if (paginator.hasStarted()) {
            paginator.reset();
        }

        // Should not throw an IllegalStateException
        paginator.next();
    }


    protected <T extends Thing> void commonTest(Paginator<T> p) {
        try {
            int numPages = 2;
            // Test that the paginator can retrieve the data
            List<Listing<T>> pages = new ArrayList<>();
            while (p.hasNext() && p.getPageIndex() <= numPages) {
                pages.add(p.next());
            }

            for (Listing<T> listing : pages) {
                // Validate the Listing
                validateModel(listing);

                if (listing.size() > 0) {
                    // Validate Listing children
                    validateModels(listing);
                } else {
                    JrawUtils.logger().warn("Listing was empty");
                }
            }
        } catch (IllegalStateException e) {
            if (e.getCause().getClass().equals(NetworkException.class)) {
                handle(e.getCause());
            }
        }
    }
}
