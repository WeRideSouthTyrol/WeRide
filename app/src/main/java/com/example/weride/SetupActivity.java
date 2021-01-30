package com.example.weride;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.squareup.picasso.Picasso;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Objects;

import de.hdodenhof.circleimageview.CircleImageView;

public class SetupActivity extends AppCompatActivity {

    private EditText username, fullName, countryName;
    private Button saveInformationButton;
    private CircleImageView profileImage;
    private ProgressDialog loadingBar;
    private FirebaseAuth mAuth;
    private DatabaseReference usersRef;
    private StorageReference userProfileImageRef;

    String currentUserId;

    final static int GALLERY_PICK = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        mAuth = FirebaseAuth.getInstance();
        currentUserId = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();
        usersRef = FirebaseDatabase.getInstance().getReference().child("Users").child(currentUserId);
        // Reference to Firebase storage, folder name "profileImages"
        userProfileImageRef = FirebaseStorage.getInstance().getReference().child("profileImages");

        username = (EditText) findViewById(R.id.setup_username);
        fullName = (EditText) findViewById(R.id.setup_full_name);
        countryName = (EditText) findViewById(R.id.setup_country_name);

        loadingBar = new ProgressDialog(this);

        saveInformationButton = (Button) findViewById(R.id.setup_save_button);

        profileImage = (CircleImageView) findViewById(R.id.setup_profile_image);

        saveInformationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveAccountSetupInformation();
            }
        });

        profileImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent galleryIntent = new Intent();
                galleryIntent.setAction(Intent.ACTION_GET_CONTENT);
                galleryIntent.setType("image/*");
                startActivityForResult(galleryIntent, GALLERY_PICK);
            }
        });

        usersRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull @NotNull DataSnapshot snapshot) {
                if (snapshot.exists()) {

                    // validation
                    if (snapshot.hasChild("profileImage")) {
                        String image = snapshot.child("profileImage").getValue().toString();
                        // placeholder is default profile image in case user does not select one
                        Picasso.get().load(image).placeholder(R.drawable.profile).into(profileImage);
                    }
                    //else {
                     //   Toast.makeText(SetupActivity.this, "Select an image first!", Toast.LENGTH_SHORT).show();
                    //}

                }
            }

            @Override
            public void onCancelled(@NonNull @NotNull DatabaseError error) {

            }
        });
    }

    // contains image crop process. For problems see https://www.youtube.com/watch?v=Ef8DyErZhII&list=PLxefhmF0pcPnTQ2oyMffo6QbWtztXu1W_&index=14&ab_channel=CodingCafe starting from 25:00
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable @org.jetbrains.annotations.Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // if 1. image is picked from gallery, 2. ..., 3. user has to select an image
        if (requestCode == GALLERY_PICK && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();

            // cropping activity, guidelines on, ratio 1:1
            CropImage.activity().setGuidelines(CropImageView.Guidelines.ON).setAspectRatio(1, 1).start(this);
        }

        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);

            if (requestCode == RESULT_OK) {
                loadingBar.setTitle("Profile image");
                loadingBar.setMessage("Updating your profile image...");
                loadingBar.show();
                loadingBar.setCanceledOnTouchOutside(true);

                Uri resultUri = result.getUri();

                // unique user id
                StorageReference filePath = userProfileImageRef.child(currentUserId + ".jpg");

                // store in Firebase Storage
                filePath.putFile(resultUri).addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onComplete(@NonNull @NotNull Task<UploadTask.TaskSnapshot> task) {
                        if (task.isSuccessful()) {
                            Toast.makeText(SetupActivity.this, "Profile image saved!", Toast.LENGTH_SHORT).show();

                            // video method is deprecated
                            final String downloadUrl = task.getResult().getStorage().getDownloadUrl().toString();
                            usersRef.child("profileImage").setValue(downloadUrl).addOnCompleteListener(new OnCompleteListener<Void>() {
                                @Override
                                public void onComplete(@NonNull @NotNull Task<Void> task) {
                                    if (task.isSuccessful()) {
                                        Intent selfIntent = new Intent(SetupActivity.this, SetupActivity.class);
                                        startActivity(selfIntent);

                                        Toast.makeText(SetupActivity.this, "Profile image stored to Database!", Toast.LENGTH_SHORT).show();
                                    } else {
                                        String message = task.getException().getMessage();
                                        Toast.makeText(SetupActivity.this, "Error occurred: " + message, Toast.LENGTH_SHORT).show();
                                    }
                                    loadingBar.dismiss();
                                }
                            });
                        }
                    }
                });
            } else {
                Toast.makeText(SetupActivity.this, "Error occurred: Image could not be cropped", Toast.LENGTH_SHORT).show();
                loadingBar.dismiss();
            }
        }

    }

    private void saveAccountSetupInformation() {
        String uname = username.getText().toString();
        String fname = fullName.getText().toString();
        String country = countryName.getText().toString();

        if (TextUtils.isEmpty(uname) || TextUtils.isEmpty(fname) || TextUtils.isEmpty(country)) {
            if (TextUtils.isEmpty(uname)) {
                Toast.makeText(this, "Username is missing!", Toast.LENGTH_SHORT).show();
            }
            if (TextUtils.isEmpty(fname)) {
                Toast.makeText(this, "Full name is missing!", Toast.LENGTH_SHORT).show();
            }
            if (TextUtils.isEmpty(country)) {
                Toast.makeText(this, "Country is missing!", Toast.LENGTH_SHORT).show();
            }
        } else {
            loadingBar.setTitle("Refuelling your bike");
            loadingBar.setMessage("Your account is almost ready...");
            loadingBar.show();
            loadingBar.setCanceledOnTouchOutside(true);

            HashMap userMap = new HashMap();
            userMap.put("username", uname);
            userMap.put("fullname", fname);
            userMap.put("country", country);
            userMap.put("status", "Part-time programmer, full-time rider!");
            userMap.put("gender", "none");
            // date of birth
            userMap.put("dob", "none");
            userMap.put("relationshipstatus", "none");
            // store info in db
            usersRef.updateChildren(userMap).addOnCompleteListener(new OnCompleteListener() {
                @Override
                public void onComplete(@NonNull @NotNull Task task) {
                    if (task.isSuccessful()) {
                        sendUserToMainActivity();
                        Toast.makeText(SetupActivity.this, "Account created!", Toast.LENGTH_SHORT).show();
                    } else {
                        String message = task.getException().getMessage();
                        Toast.makeText(SetupActivity.this, "Error occured: " + message, Toast.LENGTH_SHORT).show();
                    }
                    loadingBar.dismiss();
                }
            });
        }
    }

    private void sendUserToMainActivity() {
        Intent mainIntent = new Intent(SetupActivity.this, MainActivity.class);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(mainIntent);
        finish();
    }
}