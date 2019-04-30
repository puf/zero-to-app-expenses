const functions = require('firebase-functions');
const path = require('path');
const admin = require('firebase-admin');
const vision = require('@google-cloud/vision');
const receipt = require('./receipt');


admin.initializeApp();
/*
const visionClient = new vision.ImageAnnotatorClient({
  projectId: 'z2a-expenses'
});
const bucket = 'z2a-expenses.appspot.com';

exports.ocrReceipt= functions.storage.object().onFinalize(object => {
  console.log("Uploaded object:", object);
  return visionClient.textDetection(`gs://${object.bucket}/${object.name}`)
    .then(([detections]) => {
      if (detections.textAnnotations.length > 0) {
        var data = detections.textAnnotations[0].description;
        console.log("Data:", data);
        console.log("Detected Total:", receipt.detectTotal(data));
        console.log("Annotations:", detections.textAnnotations);
        console.log("Detected Max:", receipt.detectMax(detections.textAnnotations));
      }
    }).catch(err => {
      console.error(err);
    });
});

exports.oldScanReceipt = functions.storage.object().onFinalize(function(object) {
  const fileBucket = object.bucket; // The Storage bucket that contains the file.
  const filePath = object.name; // File path in the bucket.

  // Get the UID and file name from the path
  const uid = path.dirname(filePath).substring(filePath.indexOf('/')+1);
  const fileName = path.basename(filePath);

  // Determine the document to write the amount from this receipt to
  let expenseDoc = admin.firestore().doc(`users/${uid}/expenses/${fileName}`);

  return new Promise(function(resolve, reject) {
    setTimeout(function() {
      expenseDoc.set({
        uid: uid,
        created_at: admin.firestore.FieldValue.serverTimestamp(),
        item_cost: Math.round(Math.random() * 10000) / 100
      }).then(resolve).catch(reject);
    }, 2000 + Math.random() * 4000);
  })
});
*/

exports.scanReceipt = functions.storage.object().onFinalize(function(object) {
  const fileBucket = object.bucket; // The Storage bucket that contains the file.
  const filePath = object.name; // File path in the bucket.

  // Get the UID and file name from the path
  const uid = path.dirname(filePath).substring(filePath.indexOf('/')+1);
  const fileName = path.basename(filePath);

  // Determine the document to write the amount from this receipt to
  let expenseDoc = admin.firestore().doc(`users/${uid}/expenses/${fileName}`);

  const visionClient = new vision.ImageAnnotatorClient();
  return visionClient.textDetection(`gs://${fileBucket}/${filePath}`).then(function([result]) {
    const detections = result.textAnnotations;
    const amount = receipt.findTotal(detections);
    return expenseDoc.set({
      uid: uid,
      created_at: admin.firestore.FieldValue.serverTimestamp(),
      item_cost: amount
    })
  })

});


exports.calculateUserCost = functions.firestore
.document('users/{uid}/expenses/{expenseId}')
.onCreate((snap, context) => {
  const amount = snap.data().item_cost;
  const uid = context.params.uid;

  return admin.firestore().collection('users').doc(uid).set({
    user_cost: admin.firestore.FieldValue.increment(amount),
    last_updated: admin.firestore.FieldValue.serverTimestamp()
  }, {merge: true});
});

  exports.calculateTeamCost = functions.firestore
  .document('users/{uid}').onWrite((change, context) => {
  let old_total = change.before && change.before.data() && change.before.data().user_cost ? change.before.data().user_cost : 0;
  let new_total = change.after.data().user_cost;
  if (old_total === new_total) return true;
  console.log(`Updating all user's team_cost by ${new_total} - ${old_total}`);
  return admin.firestore().collection('users').get().then(function(querySnapshot) {
    let promises = [];
    querySnapshot.forEach(function(doc) {
      promises.push(
        doc.ref.update({ 
          team_cost: admin.firestore.FieldValue.increment(new_total - old_total) 
        })
      );
    });
    return Promise.all(promises);
  })
});
