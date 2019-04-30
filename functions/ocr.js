const vision = require('@google-cloud/vision');

// Creates a client
const client = new vision.ImageAnnotatorClient();

console.log(process.argv[2]);

// Performs label detection on the image file
client.textDetection(process.argv[2] || './20180118_27.36.jpg').then(function([result]) {
  const detections = result.textAnnotations;
  //detections.forEach(text => console.log(text));
  const regex = '^[$]?\s*(\\d+[\\.,]\\d{2})$';
  detections.forEach(text => {
    if (text.description.match(regex)) {
      console.log("Found amount: "+text.description);
    }
  });
  const amounts = detections
    .filter(text => text.description.match(regex))
    .map(text => text.description.match(regex)[1])
    .map(text => text.replace(',', '.'))
    .map(text => Number(text));
  console.log(amounts);
  console.log(Math.max.apply(null, amounts));
})
