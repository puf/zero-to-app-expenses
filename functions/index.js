const functions = require('firebase-functions');
const path = require('path');
const admin = require('firebase-admin');
const Vision = require('@google-cloud/vision');

const visionClient = Vision({
  projectId: 'z2a-expenses'
});
const bucket = 'z2a-expenses.appspot.com';

admin.initializeApp();

exports.scanReceipt = functions.storage.object().onFinalize(function(object) {
  const fileBucket = object.bucket; // The Storage bucket that contains the file.
  const filePath = object.name; // File path in the bucket.
  const contentType = object.contentType; // File content type.
  const metageneration = object.metageneration; // Number of times metadata has been generated. New objects have a value of 1.
  // Exit if this is triggered on a file that is not an image.
  if (!contentType.startsWith('image/')) {
    return console.log('This is not an image.');
  }

  console.log(filePath)

  // Get the file name and UID
  const fileName = path.basename(filePath);
  const fileDir = path.dirname(filePath).substring(filePath.indexOf('/')+1);

  return new Promise(function(resolve, reject) {
    setTimeout(function() {
      let expenseDoc = admin.firestore().doc(`users/${fileDir}/expenses/${fileName}`);
      expenseDoc.set({
        uid: fileDir,
        created_at: admin.firestore.FieldValue.serverTimestamp(),
        item_cost: Math.round(Math.random() * 10000) / 100
      }).then(resolve).catch(reject);
    }, 2000 + Math.random() * 4000);
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
