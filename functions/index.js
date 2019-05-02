const functions = require('firebase-functions');
const path = require('path');
const admin = require('firebase-admin');
const vision = require('@google-cloud/vision');
const receipt = require('./receipt');
const utils = require('./utils');

admin.initializeApp();

exports.scanReceipt = functions.storage.object().onFinalize(function(object) {
  return utils.ifEnabled(admin, 'scanReceipt').then(function() {
    const fileBucket = object.bucket; // The Storage bucket that contains the file.
    const filePath = object.name; // File path in the bucket.

    // Get the UID and file name from the path
    const uid = path.dirname(filePath).substring(filePath.indexOf('/')+1);
    const fileName = path.basename(filePath);

    const visionClient = new vision.ImageAnnotatorClient();
    return visionClient.textDetection(`gs://${fileBucket}/${filePath}`).then(function([result]) {
      const detections = result.textAnnotations;
      const amount = receipt.findTotal(detections);

      // Determine the document to write the amount from this receipt to
      let expenseDoc = admin.firestore().doc(`users/${uid}/expenses/${fileName}`);

      return expenseDoc.set({
        uid: uid,
        created_at: admin.firestore.FieldValue.serverTimestamp(),
        item_cost: amount
      });
    });
  });
});


exports.calculateUserCost = functions.firestore
.document('users/{uid}/expenses/{expenseId}')
.onCreate((snap, context) => {
  return utils.ifEnabled(admin, 'calculateUserCost').then(function() {
    const amount = snap.data().item_cost;
    const uid = context.params.uid;

    return admin.firestore().collection('users').doc(uid).set({
      user_cost: admin.firestore.FieldValue.increment(amount),
      last_updated: admin.firestore.FieldValue.serverTimestamp()
    }, {merge: true});
  });
});

  exports.calculateTeamCost = functions.firestore
  .document('users/{uid}').onWrite((change, context) => {
    return utils.ifEnabled(admin, 'calculateTeamCost').then(function() {
      let old_total = change.before && change.before.data() && change.before.data().user_cost ? change.before.data().user_cost : 0;
      let new_total = change.after.data().user_cost;
      if (old_total === new_total) return true;
      console.log(`Updating all user's team_cost by ${new_total} - ${old_total}`);
      return admin.firestore().collection('users').get().then(function(querySnapshot) {
        let promises = [];
        querySnapshot.forEach(function(doc) {
          promises.push(
            doc.ref.update({ 
              team_cost: admin.firestore.FieldValue.increment(new_total - old_total),
              last_updated: admin.firestore.FieldValue.serverTimestamp()
            })
          );
        });
        return Promise.all(promises);
      })
    });
});
