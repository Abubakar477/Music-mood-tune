# How to Fix Login & Sign Up (Firebase Setup)

The login isn't working because the app is trying to connect to a Firebase project that you might not have access to, or it's not configured for your computer.

### Step 1: Create your own Firebase Project
1. Go to [console.firebase.google.com](https://console.firebase.google.com/).
2. Click **Add project** and name it "Music Player".
3. Disable Google Analytics (to make it faster) and click **Create project**.

### Step 2: Connect the App
1. In your new project, click the **Android** icon (green robot) to add an app.
2. Enter the package name: `com.abubakar.musicplayer`
3. (Important) You need your **SHA-1** key. 
   - In Android Studio, open the **Gradle** sidebar (right side).
   - Go to `Music Player > Tasks > android > signingReport`.
   - Double-click `signingReport`.
   - Copy the `SHA1` code from the output window at the bottom.
   - Paste it into the Firebase website.
4. Click **Register app**.
5. **Download google-services.json**.
6. Replace the file on your computer at:
   `app/google-services.json`

### Step 3: Enable Authentication
1. On the Firebase website menu, click **Build > Authentication**.
2. Click **Get Started**.
3. Click **Email/Password**, enable it, and click **Save**.
4. Click **Add new provider**, select **Google**, enable it.
   - You might see a "Web SDK configuration" section here.
   - **COPY** the **Web Client ID**.
   - Click **Save**.

### Step 4: Update the Code
1. In Android Studio, open `app/src/main/res/values/strings.xml`.
2. Find `<string name="default_web_client_id">YOUR_WEB_CLIENT_ID_HERE</string>`.
3. Replace `YOUR_WEB_CLIENT_ID_HERE` with the **Web Client ID** you copied in Step 3.

### Step 5: Run the App
- Re-run the app. Login and Sign Up should now work!
