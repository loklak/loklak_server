REST2DDP.configs.push({
  name: "loklak-suggest",
  collectionName: "suggestions",
  jsonPath: "$.queries.*",
  pollInterval: 300,
  restUrl: "${apiURL}/suggest.json?count=5&orderby=messages_per_day&order=desc"
});
