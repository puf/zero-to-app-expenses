const receipt = require('./receipt');

// Format of tests is [<text>, <accepted amount>, <accepted amount>];
var tests = [];

tests.push([`Sizzle Pie
1009 East Union Street
Seattle, WA 98119
Home Of The 01 Dirty
TABLE: Lilly 25 1 Guest
Your Server was MID CASHIER
11/30/2016 7:24:27 PM
Sequence #: 0000180
ID #: 0613078
QTY PRICE
ITEM
1 $18.00
$18.00
$1.73
RG PPHO
Subtotal
Total Taxes
Grand Total
$19.73
Credit Purchase
Name
CC Type
CC Num
Reference
Approval
Server
Ticket Name
:FANG/KATHERINE
:VISA
:XXXX XXXX XXXX 9280
:6335 10355992
:059279
MID CASHIER
: Lilly 25
$19.73
Payment Amount:
Tip:
Total:
24
25%
$4.93
20%
$3.95
15%
$2.96
Yup, thats my signature, and I'11 pay
the price!
DEATH TO FALSE PIZZA
Bathroom Code: 1672`,
24, 19.73]);

for (i in tests) {
  let amount = receipt.detectTotal(tests[i][0]);
  if (!tests[i].includes(amount)) {
    console.log(`Failed test ${i}. Got ${amount}`, tests[i]);
    console.log(typeof(amount));
  }
}
