"""
The following issues were identified in the Saathi Mobile app. Go through the codebase, identify the core cause of these issues and develop an appropriate fix for the same.
1. Login Page:
    -> Enter Otp section only accepts 5 digits, where as it should accept 6 digits. This could be a ui issue where the 6th box is being hidden. Go through the codebase and fix it.
    -> Once full 6 digit otp is entered, it should auto trigger. If otp is wrong, then allow the user to reenter. 
    -> The saathi logo is small and another saathi text is added below it. Not needed. Keep only saathi logo it also has the text. Remove the text. Refer to login.png for reference. 
2. Main Talking page:
    -> Enlarge the audio waveform so that its ends fit properly horizontally. Ie both its left and right ends are touching the ends of the screen, regardless of screensize. 
    -> The entire process should be fully automatic. The user should only have to click on the waveform once for it to be triggered. Once its triggered, the user can speak, the ai responds, and so on. Similar to how gemini live mode works. The user must not have to click on the waveform everytime they need to speak. The input must automatically be sent to the ai after a silence is observed for 1-1.5s. Fix this issue properly.
    -> The Main talking page isnt working, following error was observed.2026-05-02 20:31:03.136 26092-30918 MainVM                  com.sohanreddy.sevak                 E  Sarvam STT failed: HTTP 403  (Fix with AI)
                                                                                                    retrofit2.HttpException: HTTP 403 
                                                                                                    	at retrofit2.KotlinExtensions$await$2$2.onResponse(KotlinExtensions.kt:53)
                                                                                                    	at retrofit2.OkHttpCall$1.onResponse(OkHttpCall.java:164)
                                                                                                    	at okhttp3.internal.connection.RealCall$AsyncCall.run(RealCall.kt:519)
                           
                                                                                                    	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1154)
                                                                                                    	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:652)
                                                                                                    	at java.lang.Thread.run(Thread.java:1564)

 
    Use web search mcp if needed, identify the core cause of this and fix it appropriately.
    -> Also when the user clicks on the waveform, a weird rectangle is being shown around the waveform.  Like a hover effect but on click. This should be removed.

to fix these issue first go through the codebase in detail, identify the root cause of these issues, use web search mcp to find appropriate fixes and only once confirmed, implement the fixes. 

"""



"""
Major Design Revamp:
1. Currently when the user logs in they are directly redirected to the main talking page. Now since we are adding a ton more features to the app, we need to do a major design revamp. 
2. After the user logs in, they should be taken to a home page/dashboard. (Refer to the attached Screenshot for reference) This page will have the following options:
    -> Saathi - redirects to the main talking page where the user can talk to the ai and have a conversation. This is the current main talking page that we have, we will just be redirecting to it from the dashboard when the user clicks on saathi.
    -> Pulse - Currently placeholder
    -> Radar - Currently placeholder
The image files for all these sections are present in the app\src\main\res\drawable\section folder. Update accordingly.
3. Further a Dock/Footer needs to be added to the app, which will be visible on all pages. This dock will have 5 redirects, icons for each are present in the app\src\main\res\drawable\dock folder. The redirects are as follows:
    -> Home - redirects to the dashboard/homepage
    -> Pulse - placeholder for now
    -> Saathi - redirects to the main talking page
    -> Radar - placeholder for now
    -> Me - placeholder for now
4. The design of the dashboard and the dock should be such that it is easily extendable in the future when we add more features.
5. Refer to the attached screenshot for the design and use the assets provided in the app\src\main\res\drawable folder to implement the design.

"""

"""
SAATHI VOICE INTERFACE REDESIGN:
1. Currently the voice interface is not updated to the new design. We need to do a redesign of the voice interface to make it more user friendly and visually appealing.
2. Background has been changed, rest of the components need to be updated accordingly.
    -> Currently the waveform is multi line based. Update it to a vertical bar wafeform, all functionality same just the design updated. Refer to the attached screenshot for reference.
    -> Update the desing of this page to match the new design language of the app. Refer to the design language of the home page/dashboard and implement a similar design for the voice interface. 
3. Settings Drawer: Currently the settings drawer just updates the language the user converses in. Now we need to add a ton more features to the settings drawer. Focus on design and UI only first, functionality will be added later. The new settings drawer should have the following sections: 
    -> Language Dropdown - same as current functionality, allows user to select the language they want to converse in.
    -> Upload Documents - This section will allow users to upload documents that the ai can refer to during the conversation. For now just add a placeholder button for uploading documents, functionality will be added later. 
    -> Report Viewer - One of the goals of saathi is that it can make detailed reports of the users health, conditions, activities and other such details similar to a doctor. Now, in the future we will be adding a toolcall to the AI that will allow it to fill in pre designed report templates based on the conversation it has with the user. This section will allow users to view those reports in a visually appealing manner. For now just add a placeholder button for viewing reports and editing report templates. Create appropriate template for the reports viewer section. Obviously include metadata like creation date, last updated date, name of the report etc in the design of the report viewer. And also Add the fields that the AI will be filling in the future in the report template, so that the design is ready by the time we add the functionality.
    -> Now there wont just be a single report, multiple reports will be generated based on the conversations the user has with the AI. So the report viewer should be designed in such a way that it can accomodate multiple reports and display them in an organized manner. FUll crud functionality should be there in the report viewer, allowing users to create new reports, view existing reports, edit reports and delete reports. 
    -> For the forms, user uploaded reports, etc ensure full crud functionality is there. Also all storage will be fully local for now, so ensure that the design and implementation is done accordingly.
    -> DO NOT IMPLEMENT ANY AI FUNCTIONALITY FOR NOW, JUST DESIGN THE UI AND IMPLEMENT THE UI. MAKE SURE TO DESIGN IT IN SUCH A WAY THAT IT CAN ACCOMODATE THE FUNCTIONALITY WE WILL BE ADDING IN THE FUTURE WITHOUT ANY MAJOR CHANGES TO THE DESIGN. 
NOTES:
1. KEEP IN MIND THAT THIS IS ONLY A DESIGN REVAMP, DO NOT IMPLEMENT ANY FUNCTIONALITY FOR NOW, JUST DESIGN THE UI AND IMPLEMENT THE UI. MAKE SURE TO DESIGN IT IN SUCH A WAY THAT IT CAN ACCOMODATE THE FUNCTIONALITY WE WILL BE ADDING IN THE FUTURE WITHOUT ANY MAJOR CHANGES TO THE DESIGN.
2. REFER TO THE EXISTING PARTS OF THE APP FOR DESIGN LANGUAGE, COLOR SCHEME, COMPONENT DESIGN ETC AND MAINTAIN CONSISTENCY THROUGHOUT THE APP. ALSO REFER TO THE ATTACHED SCREENSHOT FOR REFERENCE FOR THE DESIGN LANGUAGE AND COMPONENT DESIGN. IGNORE LOGIN PAGE AND OTP PAGES SINCE THESE HAVE NOT BEEN UPDATED TO THE NEW DESIGN LANGUAGE YET, FOCUS ON THE MAIN TALKING PAGE AND THE DASHBOARD/HOME PAGE FOR REFERENCE.

"""

"""
I found the key surfaces: voice UI/settings in `MainScreen.kt`, waveform in `WaveformCanvas.kt`, dashboard/dock design language in `AuthenticatedAppShell.kt`.

Before I implement, a few open questions so I don’t accidentally build the wrong shape:

1. Should the settings remain a **bottom sheet**, or do you want it changed into a true **side drawer**? You called it “Settings Drawer,” but the current app uses a bottom sheet. Use Bottom sheet, but independent screens for all forms etc. that open when you click on the respective buttons in the settings sheet.

2. For “UI only” plus “full CRUD,” should CRUD be:
   - working local CRUD persisted on-device, likely via SharedPreferences/JSON for now?

3. For Upload Documents, do you want only a placeholder upload button, or should I design a local document list too: uploaded files, date added, type, rename/delete actions, empty state? Fully funcitonal, local CRUD for document management.

4. For Report Viewer, should I add demo reports by default so the UI looks populated, or start with an empty state and a “Create report” flow? Add demo reports and also design the create report flow.

5. What report templates should be included initially? I can design a general health summary template with fields like symptoms, medications, vitals, allergies, history, lifestyle, doctor notes, recommendations, and follow-up plan, but tell me if you want specific templates such as diabetes, fever, maternal health, elderly care, or chronic condition tracking. Design the general health summary template for now, we can add specific templates in the future.

6. Should users create/edit reports through a full-screen editor, inside the settings sheet/drawer, or via a nested modal/detail panel? fUll screen editor for creating and editing reports, accessed via the settings sheet.

7. Should the voice page keep the current top-right map and settings icons plus bottom-right screen-share logo, or should I restyle/reposition them to match the new dashboard language more closely? Remove bottom-right screen-share logo, restyle and reposition the settings icon to match the new design language, and remove the map icon. The map icon functionality should be moved to the Radar section in the dashbord and dock.

8. For the vertical bar waveform, should it be full-width like the current waveform, or more compact and centered like the screenshot reference? Screnshot reference, compact and centered.

9. Should the waveform bars react exactly to current `audioAmplitude`, including listening/speaking/processing states, while only changing the visual style? I’m assuming yes. Yes, the waveform bars should react to the current `audioAmplitude` and all the states, but the visual style should be updated to match the new design language.

10. Do you want the voice page to stay mostly minimal, with just waveform/status/actions, or should it include richer dashboard-like panels such as “Current mode,” “Language,” “Reports,” and “Documents” shortcuts? Minimal

Once you answer these, I’ll implement the redesign and run the build successfully before handing it back.

"""


"""
UI REFINEMENET/ENHANCEMENT FOR THE PULSE SECTION
1. Currently the Pulse section has been implemented with default UI and it needs to be updated to match the rest of the app's design language and to make it more visually appealing and user friendly.
2. First Update the overall UI, ie background and styling to make it match the rest of the apps design system.
3. Replace the existing Heart rate and Sp02 cards with the heart-rate-section-box and spo2-section-box images and turn them to cards, redirects remains same.
4. Heart Rate detection page: Use the heart-rate-bg image for background then update it so that the heart rate is denoted on top of the heart, also add a small circle on top that shows live camera feed so that the user can see which camera to cover. (refer to attached screenshot for reference)
5. Similarly for the SPO2 detection page use the spo2-bg image for background and make the same changes as for the heart rate page.

 

"""