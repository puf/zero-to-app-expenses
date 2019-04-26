//
//  ViewController.swift
//  z2a-expenses
//
//  Copyright © 2019 Firebase. All rights reserved.
//

import UIKit
import Firebase
import FirebaseUI

class ViewController: UIViewController, UINavigationControllerDelegate, UIImagePickerControllerDelegate, FUIAuthDelegate {
    
    private var imagePickerController = UIImagePickerController()
    
    private var authUI: FUIAuth
    private var auth: Auth
    private var storage: Storage
    private var firestore: Firestore
    
    @IBOutlet weak var yourSpendLabel: UILabel!
    @IBOutlet weak var teamSpendLabel: UILabel!
    @IBOutlet weak var lastItemLabel: UILabel!
    
    required init?(coder aDecoder: NSCoder) {
        FirebaseApp.configure()
        self.authUI = FUIAuth.defaultAuthUI()!
        self.auth = Auth.auth()
        self.storage = Storage.storage()
        self.firestore = Firestore.firestore()
        super.init(coder: aDecoder)
        return
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        
        // Initialize FirebaseUI
        self.authUI = FUIAuth.defaultAuthUI()!
        self.authUI.providers = [
            FUIGoogleAuth()
        ]
        self.authUI.delegate = self
        
        // Initialize UINavigationController
        self.title = "My I/O Expenses"
        self.navigationItem.rightBarButtonItem = UIBarButtonItem(barButtonSystemItem: .camera, target: self, action: #selector(selectPhoto))
        self.navigationItem.leftBarButtonItem = UIBarButtonItem(title: "", style: .plain, target: self, action: #selector(toggleLogin))
        
        // Initialize UIImagePickerController
        self.imagePickerController.allowsEditing = false
        self.imagePickerController.sourceType = .photoLibrary // change to .camera when using an actual device
        self.imagePickerController.delegate = self
        
    }
    
    func uploadReceiptImage(data: Data) {
        let storageMetadata = StorageMetadata()
        storageMetadata.contentType = "image/jpeg"
        
        let userId = self.auth.currentUser?.uid ?? "12345"
        let expenseId = NSUUID().uuidString
        
        let storageRef = storage.reference().child("receipts/\(userId)/\(expenseId)")
        storageRef.putData(data, metadata: nil) { (metadata, error) in
            if (error != nil) {
                print(error.debugDescription)
            } else {
                let alertViewController = UIAlertController(title: "Upload succeeded!", message: "Your upload succeeded", preferredStyle: .alert)
                alertViewController.addAction(UIAlertAction(title: "Ok", style: UIAlertAction.Style.default, handler: { (action) in
                    self.dismiss(animated: true, completion: nil)
                }))
                self.present(alertViewController, animated: true, completion: nil)
            }
        }
    }
    
    override func viewWillAppear(_ animated: Bool) {
        if (self.auth.currentUser != nil) {
            self.navigationItem.leftBarButtonItem?.title = "Log out"
            self.listenForExpenses()
        } else {
            self.navigationItem.leftBarButtonItem?.title = "Log in"
            self.presentAuthViewController()
        }
    }
    
    @objc func toggleLogin() {
        if (self.auth.currentUser != nil) {
            do {
                try self.auth.signOut()
                self.navigationItem.leftBarButtonItem?.title = "Log in"
            } catch {
                print("Failed to sign user out: \(error)")
            }
        } else {
            self.navigationItem.leftBarButtonItem?.title = "Log out"
            self.presentAuthViewController()
        }
    }
    
    func presentAuthViewController() {
        let authViewController = self.authUI.authViewController()
        self.present(authViewController, animated: true, completion: nil)
    }
    
    @objc func selectPhoto() {
        if (self.auth.currentUser != nil) {
            self.present(self.imagePickerController, animated: true, completion: nil)
        }
    }
    
    // UIImagePickerControllerDelegate methods
    func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]) {
        guard let image = info[.originalImage] as? UIImage else { return }
        let imageData = image.jpegData(compressionQuality: 1.0)
        self.uploadReceiptImage(data: imageData!)
        self.dismiss(animated: true, completion: nil)
    }
    
    // Firestore methods
    func listenForExpenses() {
        let uid = self.auth.currentUser?.uid ?? ""
        self.firestore.collection("users").document(uid)
            .addSnapshotListener { (documentSnapshot, error) in
                guard let document = documentSnapshot else {
                    print("Error fetching document: \(error!)")
                    return
                }
                guard let data = document.data() else {
                    print("Document data was empty.")
                    return
                }
                let yourSpend = data["user_cost"] as? Double ?? 0.00
                let teamSpend = data["team_cost"] as? Double ?? 0.00
                self.yourSpendLabel?.text = String(yourSpend)
                self.teamSpendLabel?.text = String(teamSpend)
            }
        
        self.firestore.collection("users").document(uid).collection("expenses")
            .order(by: "created_at", descending: true)
            .limit(to: 1)
            .addSnapshotListener { (querySnapshot, error) in
                guard let documents = querySnapshot?.documents else {
                    print("Error fetching documents: \(error!)")
                    return
                }
                let itemCost = documents.first?["item_cost"] as? Double ?? 0.00
                self.lastItemLabel?.text = String(itemCost)
                
            }
    }
    
    // FUIAuthDelegate methods
    func authUI(_ authUI: FUIAuth, didSignInWith authDataResult: AuthDataResult?, error: Error?) {
        if (error != nil) {
            print(error.debugDescription)
        } else {
            print("Login succeeded!")
        }
    }

}

