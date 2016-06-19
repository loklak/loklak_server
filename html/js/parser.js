/*
 * Utility implementation of node.sourceIndex()
 *
 * Returns the node's source-order position within the document.
 */
if(typeof document.documentElement.sourceIndex == "undefined") {
  HTMLElement.prototype.__defineGetter__("sourceIndex", (function(indexOf) {
    return function sourceIndex(){
      return indexOf.call(this.ownerDocument.getElementsByTagName("*"), this);
    };
  })(Array.prototype.indexOf));
}

/**
 * Simple string/position tuple.  Extracted strings need to retain
 * information about their place in the document for when we group
 * them later.
 */
function StringNode(string, position) {
  this.string = string;
  this.position = position;
};
StringNode.prototype = new Object();

StringNode.prototype.toString = function() {
  return "<" + this.string + "(" + this.position + ")>";
}

/**
 * This is similar to a jQuery list of HTML Nodes.  I need to be able to
 * call methods on it, and each contained String needs an associated position.
 */
function StringNodeList() {
  this.multiple = false;
};
StringNodeList.prototype = new Array();

StringNodeList.prototype.toString = function() {
  var buffer = "<StringNodeList[";
  for(var i = 0; i < this.length; i++){
    if(i > 0) buffer += ", ";
    buffer += this[i].toString();
  }
  return buffer + "]>";
}

/**
 * Creates an array of strings that mirrors this StringNodeList.
 */
StringNodeList.prototype.simple = function() {
  var array = [];
  jQuery.each(this, function() {
    array.push(this.string);
  });  
  return array;
}

/**
 * The core function is used to convert jQuery objects to 
 * StringNodeLists. i.e.:
 *
 *   var jq = jQuery('a.linky');
 *   var stringNodeList = jq.extract(function(domNode){
 *     // ...
 *     return someStringFromInsideTheDomNode;
 *   });
 * 
 * The following shortcut arguments are also available:
 * - extract() 
 *   => function(node) { return jQuery(node).text(); }
 * - extract("@foo") 
 *   => function(node) { return jQuery(node).attr("foo"); }
 * - extract(/regex/) 
 *   => function(node) { return (/regex/.execute(jQuery(node).text())[0]; }
 */
jQuery.fn.extract = function(func) {
  if(!func){
    func = function(node) {
      return jQuery(node).text();
    };
  }

  // extract(/regex/)
  if(func instanceof RegExp) {
    var re = func;
    func = function(node) {
      var text = jQuery(node).text();
      return re.exec(text)[0];
    };
  }
  
  // extract("@attribute")
  if(typeof(func) == "string" && func[0] == "@") {
    var attr = func.substring(1);
    func = function(node) {
      return jQuery(node).attr(attr);
    };
  }
  
  var list = new StringNodeList();
  this.each(function(){
    list.push(new StringNode(func(this), this.sourceIndex));
  });
  return list;
}

function pQuery(){};

/**
 * Slightly voodoo.  This function cleans up any remaining jQuery
 * objects by running extract on them, then tries to group all of the 
 * StringNodeLists by their implicit page ordering.  Then it simplifies
 * the resulting data structure to vanilla object/arrays.
 */
pQuery.extractAndGroup = function(parselet) {
  pQuery.extract(parselet);
  pQuery.group(parselet)
  return parselet;
}

/**
 * Builds a giant array of nodes as a pre-processing step for grouping.
 */
pQuery.compileNodes = function(object) {
  var nodes = [];
  jQuery.each(object, function(key, value) {    
    if(value instanceof StringNodeList) {
      jQuery.each(value, function(i, node) {
        node.multiple = value.multiple;
        node.key = key;
        nodes.push(node);
      });
    }
  });
  return nodes.sort(function(a,b) { return a.position - b.position; });
}

/**
 * This recursively looks for arrays with a single object child (i.e.: "[{...}]").
 * This will get grouped.  The actual grouping mechanics are: (1) make references in
 * each node to its key. (2) Throw all the nodes into a giant array. (3) Sort the
 * array in page order. (4) Iterate the array, dropping nodes into the current group. 
 * (4a) Add an additional group if the current group is full.
 */
pQuery.group = function(parselet) {
  jQuery.each(parselet, function(key, value) {    
    if(value instanceof Array && typeof(value[0]) == "object") {
      var allNodes = pQuery.compileNodes(value[0]);
      var node;
      var groups = [];
      var group = {};
      groups.push(group);
      while(node = allNodes.shift()) {
        if(!node.multiple && group[node.key]){
          group = {};
          groups.push(group)
        }
        if(node.multiple) {
          if(!group[node.key]) group[node.key] = [];
          group[node.key].push(node.string);
        } else {
          group[node.key] = node.string;
        }
      }
      parselet[key] = groups;
    } else if(typeof(value) == "object" && !(value instanceof StringNodeList)) {
      pQuery.group(value); //recurse
    }
  });  
}

/**
 * This does the implied conversion from jQuery objects to StringNodeLists.
 * It also transforms the array: [$(...)] into a (Multiple) StringNodeList.
 * You shouldn't ever have to call this method; it's a preprocessing step
 * to extractAndGroup().
 */
pQuery.extract = function(parselet) {
  jQuery.each(parselet, function(key, value) {
    if(typeof(value) == "array") { 
      if(value[0] instanceof StringNodeList) {
        parselet[key] = value = value[0];
        value.multiple = true;
      } else {
        pQuery.extract(value);
      }
    } else if(value instanceof jQuery) {
      parselet[key] = value = value.extract();
    } else if(value instanceof StringNodeList) {
      //Nothing
    } else if(typeof(value) == "object") {
      pQuery.extract(value);
    }
  });
}
