# Example of Streaming Messages from loklak Server

## Enabling Streaming

To enable streaming in loklak, we first need to enable the stream flag in `conf/config.properties` - 

```properties
stream.enabled = true
```

## Deciding the channels

In order to listen to a stream, we first need to decide the channel which we want to listen on.

To get an overview of channels available in loklak, take a look at [Stream Channels](StreamChannels.md).

## Connecting to Stream Using `EventSource`


For this example, we take the peer name as `my.loklak.peer`, i.e. we can access the loklak API page at `http://my.loklak.peer`. It is assumed that this peer has streaming enabled.
 
Let us try to access all the Tweets with hashtag `#OpenSource`

```javascript
var eventSource = new EventSource('http://my.loklak.peer/api/stream.json?channel=twitter%2Fhashtag%2FOpenSource');
```

## Handling Events
```javascript
eventSource.onopen = function() {
    console.log('Streaming Started at channel twitter/hashtag/OpenSource');
};

eventSource.onmessage = function(event) {
    console.log('Data received from loklak - ' + event.data);
};

eventSource.onerror = function() {
    console.log('Eventsource failed!')
}
```

Take a look at live example [here](stream.html).

> More about [EventSource](https://developer.mozilla.org/en/docs/Web/API/EventSource).
