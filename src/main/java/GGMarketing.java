import com.beust.jcommander.Parameter;
import com.google.ads.googleads.lib.GoogleAdsClient;
import com.google.ads.googleads.v1.common.Metrics;
import com.google.ads.googleads.v1.errors.GoogleAdsError;
import com.google.ads.googleads.v1.errors.GoogleAdsException;
import com.google.ads.googleads.v1.resources.*;
import com.google.ads.googleads.v1.services.*;
import com.google.ads.googleads.v1.utils.ResourceNames;
import com.google.auth.oauth2.ClientId;
import com.google.auth.oauth2.UserAuthorizer;
import com.google.auth.oauth2.UserCredentials;
import com.google.common.collect.ImmutableList;

import java.io.*;
import java.net.URI;
import java.net.URL;

public class GGMarketing {
    private static final int PAGE_SIZE = 1000;
    private static String CUSTOMER_ID_M = "4934208080";
    private static String CUSTOMER_ID_C = "8577947674";

    private static final ImmutableList<String> SCOPES =
            ImmutableList.<String>builder().add("https://www.googleapis.com/auth/adwords").build();

    private static final String CALLBACK_URI = "urn:ietf:wg:oauth:2.0:oob";

    private static class GetKeywordStatsParams extends CodeSampleParams {

        @Parameter(names = ArgumentNames.CUSTOMER_ID, required = true)
        private Long customerId;
    }

    public static void main(String[] args) {
        System.out.println("Start");

        GetKeywordStatsParams params = new GetKeywordStatsParams();
        if (!params.parseArguments(args)) {

            // Either pass the required parameters for this example on the command line, or insert them
            // into the code here. See the parameter class definition above for descriptions.
            params.customerId = Long.parseLong(CUSTOMER_ID_M);
        }
        params.customerId = Long.parseLong(CUSTOMER_ID_C);

        GoogleAdsClient googleAdsClient;
        try {
            File propertiesFile = new File("res/ads1.properties");
            googleAdsClient =
                    GoogleAdsClient.newBuilder()
                            .fromPropertiesFile(propertiesFile).build();
        } catch (FileNotFoundException fnfe) {
            System.err.printf(
                    "Failed to load GoogleAdsClient configuration from file. Exception: %s%n", fnfe);
            return;
        } catch (IOException ioe) {
            System.err.printf("Failed to create GoogleAdsClient. Exception: %s%n", ioe);
            return;
        }
        System.out.println(googleAdsClient.getLoginCustomerId());

        try {
            getAccountInformation(googleAdsClient, params.customerId);
//            getManagerInformation(googleAdsClient, params.customerId);
            runExample(googleAdsClient, params.customerId);
        } catch (GoogleAdsException gae) {
            // GoogleAdsException is the base class for most exceptions thrown by an API request.
            // Instances of this exception have a message and a GoogleAdsFailure that contains a
            // collection of GoogleAdsErrors that indicate the underlying causes of the
            // GoogleAdsException.
            System.err.printf(
                    "Request ID %s failed due to GoogleAdsException. Underlying errors:%n",
                    gae.getRequestId());
            int i = 0;
            for (GoogleAdsError googleAdsError : gae.getGoogleAdsFailure().getErrorsList()) {
                System.err.printf("  Error %d: %s%n", i++, googleAdsError);
            }
        }
    }

    private static void getManagerInformation(GoogleAdsClient googleAdsClient, long customerId){
        CustomerManagerLinkServiceClient m = googleAdsClient.getLatestVersion().createCustomerManagerLinkServiceClient();
        String customerResourceName = ResourceNames.customer(customerId);
        CustomerManagerLink ml = m.getCustomerManagerLink(customerResourceName);
        System.out.println("Manager customer: " + ml.getManagerCustomer());
    }

    private static void getAccountInformation(GoogleAdsClient googleAdsClient, long customerId){
        try (CustomerServiceClient customerServiceClient = googleAdsClient.getLatestVersion().createCustomerServiceClient()) {
            String customerResourceName = ResourceNames.customer(customerId);
            Customer customer = customerServiceClient.getCustomer(customerResourceName);
            // Print account information.
            System.out.printf(
                    "Customer with ID %d, descriptive name '%s', currency code '%s', timezone '%s', "
                            + "tracking URL template '%s' and auto tagging enabled '%s' was retrieved.%n",
                    customer.getId().getValue(),
                    customer.getDescriptiveName().getValue(),
                    customer.getCurrencyCode().getValue(),
                    customer.getTimeZone().getValue(),
                    customer.getTrackingUrlTemplate().getValue(),
                    customer.getAutoTaggingEnabled().getValue());
        }
    }

    /**
     * Runs the example.
     *
     * @param googleAdsClient the Google Ads API client.
     * @param customerId the client customer ID.
     * @throws GoogleAdsException if an API request failed with one or more service errors.
     */
    private static void runExample(GoogleAdsClient googleAdsClient, long customerId) {
        try (GoogleAdsServiceClient googleAdsServiceClient =
                     googleAdsClient.getLatestVersion().createGoogleAdsServiceClient()) {
            String searchQuery =
                    "SELECT campaign.id, "
                            + "campaign.name, "
                            + "ad_group.id, "
                            + "ad_group.name, "
                            + "ad_group_criterion.criterion_id, "
                            + "ad_group_criterion.keyword.text, "
                            + "ad_group_criterion.keyword.match_type, "
                            + "metrics.impressions, "
                            + "metrics.clicks, "
                            + "metrics.cost_micros "
                            + "FROM keyword_view "
                            + "WHERE segments.date DURING LAST_7_DAYS "
                            + "AND campaign.advertising_channel_type = 'SEARCH' "
                            + "AND ad_group.status = 'ENABLED' "
                            + "AND ad_group_criterion.status IN ('ENABLED', 'PAUSED') "
                            // Limits to the 50 keywords with the most impressions in the date range.
                            + "ORDER BY metrics.impressions DESC "
                            + "LIMIT 50";

            // Creates a request that will retrieve all keyword statistics using pages of the specified
            // page size.
            SearchGoogleAdsRequest request =
                    SearchGoogleAdsRequest.newBuilder()
                            .setCustomerId(Long.toString(customerId))
                            .setPageSize(PAGE_SIZE)
                            .setQuery(searchQuery)
                            .build();
            // Issues the search request.
            GoogleAdsServiceClient.SearchPagedResponse searchPagedResponse = googleAdsServiceClient.search(request);
            // Iterates over all rows in all pages and prints the requested field values for the keyword
            // in each row.
            for (GoogleAdsRow googleAdsRow : searchPagedResponse.iterateAll()) {
                Campaign campaign = googleAdsRow.getCampaign();
                AdGroup adGroup = googleAdsRow.getAdGroup();
                AdGroupCriterion adGroupCriterion = googleAdsRow.getAdGroupCriterion();
                Metrics metrics = googleAdsRow.getMetrics();

                System.out.printf(
                        "Keyword text '%s' with "
                                + "match type '%s' "
                                + "and ID %d "
                                + "in ad group '%s' "
                                + "with ID %d "
                                + "in campaign '%s' "
                                + "with ID %d "
                                + "had %d impression(s), "
                                + "%d click(s), "
                                + "and %d cost (in micros) "
                                + "during the last 7 days.%n",
                        adGroupCriterion.getKeyword().getText().getValue(),
                        adGroupCriterion.getKeyword().getMatchType(),
                        adGroupCriterion.getCriterionId().getValue(),
                        adGroup.getName().getValue(),
                        adGroup.getId().getValue(),
                        campaign.getName().getValue(),
                        campaign.getId().getValue(),
                        metrics.getImpressions().getValue(),
                        metrics.getClicks().getValue(),
                        metrics.getCostMicros().getValue());
            }
        }
    }
}
