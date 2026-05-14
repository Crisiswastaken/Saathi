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

"""