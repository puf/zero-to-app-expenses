let keywords = ["payment amount", "amount due", "amount", "grand total", "total"];
let moneyRegex = /^\$?([0-9]+(\.[0-9]{1,2})?)$/;
let debug = true;

const parseAmount = function(line) {
  let result = moneyRegex.exec(line);
  if (result === null) return null;
  else return result[1];
};

exports.detectTotal = function(raw) {
  let text = raw.split("\n");
  var candidates = [];
  for (var i = 0; i < text.length; i++) {
    for (word of keywords) {
      if (text[i].toLowerCase().indexOf(word) !== -1) {
        // check if the group before was an amount, and prefer it over having an
        // amount listed before it
        var amountFound = false;
        if (i < text.length-1) {
          var amount = parseAmount(text[i+1]);
          if (amount !== null) {
            candidates.push([word, amount]);
            amountFound = true;
          }
        }
        // check if the next group was an amount
        if (i > 0 && !amountFound) {
          var amount = parseAmount(text[i-1]);
          if (amount !== null) {
            candidates.push([word, amount]);
          }
        }
        break; // only capture the "first" of the keywords found since we look
               // for both "grand total" and "total" and such
      }
    }
  }
  if (debug) {
    console.log("Candidate amounts:", candidates);
  }

  // choose the max candidate ... I guess? Or most repeated one?
  if (candidates.length === 0) {
    return null;
  } else {
    candidates = candidates.map(c => Number(c[1]));
    return Math.max.apply(null, candidates);
  }
};
