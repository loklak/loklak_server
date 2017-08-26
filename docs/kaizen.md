# Kaizen Harvester

> Kaizen is an alternative approach to do Harvesting, it focuses on query and information collecting to generate more queries. Kaizen also uses Twitter API to get trending topics to increase the chance of getting relevant or wanted messages (This feature is optional).

Source: [#960](https://github.com/loklak/loklak_server/pull/960).

## Generating New Queries
Kaizen harvester generates new queries in 3 ways -
1. Getting trending queries from Twitter.
2. Grab suggestions from backend.
3. Get more data out of scraped timelines.
  a. Hashtags in timeline.
  b. User mentions in timeline.
  c. Tweets older than oldest in timeline.
  d. Tweets from areas near to tweets in timeline.

## Enabling Kaizen Harvester

To enable Kaizen harvester, set `harvester.type` to `kaizen` in `config.properties`.

## Configuring Kaizen Harvester
The following fields can be set in `config.properties` to tweak with Kaizen harvester -

<!-- markdown+ -->
| Field | Description | Default |
|-------|-------------|---------|
| `harvester.kaizen.suggestions_count` | The amount of suggestions to request. | 1000 |
| `harvester.kaizen.suggestions_random` | The amount of randomly selected suggestions to add. | 5 |
| `harvester.kaizen.place_radius` | The radius for location/place queries (in miles) | 5 |
| `harvester.kaizen.queries_limit` | The query limit (setting this to 0 or below means infinite). | 500 |
| `harvester.kaizen.verbose` | Verbosity (gives information to logs, if enabled) | true |
<!-- endmarkdown+ -->
