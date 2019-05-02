exports.ifEnabled = function ifEnabled(admin, feature) {
  console.log("Checking if feature '"+feature+"' is enabled");
  return new Promise((resolve, reject) => {
    admin.database().ref("/config/features")
      .child(feature)
      .once('value')
      .then(snapshot => {
        if (snapshot.val()) {
          resolve(snapshot.val());
        }
        else {
          reject("No value or 'falsy' value found for feature '"+feature+"'");
        }
      });
  });
}