# Stream Channels

## Generic

The following channel contains a stream for all the messages collected by loklak:

```
- all
```

## Twitter

The following are Twitter specific channels:

```
- twitter
- twitter/mention/<username>      # Mentions of a user
- twitter/user/<username>         # By a user
- twitter/hashtag/<hashtag>       # Including hashtag
- twitter/country/<country_code>  # For a specefic country
- twitter/text/<token>            # Containing word token
```

**Note**: Country codes are in [`ISO 3166-1 alpha-2`](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2) format.
