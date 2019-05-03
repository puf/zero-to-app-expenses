const functions = require('firebase-functions');
const path = require('path');
const admin = require('firebase-admin');
const vision = require('@google-cloud/vision');
const receipt = require('./receipt');
const utils = require('./utils');

admin.initializeApp();

exports.scanReceipt = functions.storage
.object().onFinalize((object) => {
  return utils.ifEnabled(admin, '1_scanReceipt').then(() => {
    // Determine the bucket and path that triggered us
    const fileBucket = object.bucket;
    const filePath = object.name;

    // Get the UID and file name from the path
    const uid = path.dirname(filePath).substring(filePath.indexOf('/')+1);
    const fileName = path.basename(filePath);

    // Call Cloud Vision to find text in the receipt
    const visionClient = new vision.ImageAnnotatorClient();
    return visionClient.textDetection(`gs://${fileBucket}/${filePath}`).then(([result]) => {
      const detections = result.textAnnotations;

      // Find the total amount in this receipt
      const amount = receipt.findTotal(detections);

      // Determine the document to write the amount from this receipt to
      let expenseDoc = admin.firestore().doc(`users/${uid}/expenses/${fileName}`);

      // Write the amount to this new document
      return expenseDoc.set({
        created_at: admin.firestore.FieldValue.serverTimestamp(),
        item_cost: amount
      });
    });
  });
});


exports.calculateUserCost = functions.firestore
.document('users/{uid}/expenses/{expenseId}').onCreate((snap, context) => {
  return utils.ifEnabled(admin, '2_calculateUserCost').then(() => {
    // Determine the UID and the amount of the receipt
    const amount = snap.data().item_cost;
    const uid = context.params.uid;

    // Increase the user_amount for the user
    return admin.firestore().collection('users').doc(uid).set({
      user_cost: admin.firestore.FieldValue.increment(amount),
      last_updated: admin.firestore.FieldValue.serverTimestamp()
    }, {merge: true});
  });
});

exports.calculateTeamCost = functions.firestore
.document('users/{uid}').onWrite((change, context) => {
  return utils.ifEnabled(admin, '3_calculateTeamCost').then(() => {
    // Determine how much the user_cost was changed
    let old_total = change.before && change.before.data() ? change.before.data().user_cost : 0;
    let new_total = change.after.data().user_cost;

    // Exit straight away if there was no change
    if (old_total === new_total) return true;

    // Load all user documents
    return admin.firestore().collection('users').get().then((querySnapshot) => {
      let promises = [];
      querySnapshot.forEach((doc) => {
        promises.push(
          // Increase the team_amount for this user
          doc.ref.update({ 
            team_cost: admin.firestore.FieldValue.increment(new_total - old_total),
            last_updated: admin.firestore.FieldValue.serverTimestamp()
          })
        );
      });
      // Tell Cloud Functions when all updates are done
      return Promise.all(promises);
    })
  });
});
