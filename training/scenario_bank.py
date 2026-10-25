"""Per-language scenario banks + synthesizer for the Quiver training set.

`synthesize(rng, quotas)` composes seed-format rows (same schema as
dataset/seed.jsonl) from sentence templates × slot banks, per language:

  note_checklist   save N items as a checklist note
  note_text        save a small piece of text (password, code, reading)
  task             one reminder with a relative date/daypart
  event            one calendar event (appointment / meeting / call)
  note_task        checklist note + "remind me to buy them" (2 sequential steps)
  recipe (paired)  round 1: ONLY web_search — even though the user asked for a
                   note and a reminder too; round 2 (findings embedded): note
                   with the ingredients AS A CHECKLIST + buy reminder
                   (+ sometimes append the cooking steps). This teaches the
                   search-then-act ordering the agent runtime enforces.
  append           add items to an existing note
  rate / convert   currency questions with localized currency names
  trash            screenshot-trash check
  chat             greetings / capability questions / polite refusals
  agenda (paired)  round 1: ONLY list_agenda; round 2: summarize the returned
                   agenda (or report a free day) with no further tool calls
  readnote (paired) round 1: ONLY read_note; round 2: read the items back — or,
                   in the action variant, set the buy-reminder the user asked for

Dates in outputs use the {today}/{tomorrow}/{day_after}/{friday}/{monday}/
{saturday} placeholders that generate_dataset.py renders against anchor dates.
"""

from __future__ import annotations


def _join(items: list[str], and_word: str) -> str:
    return items[0] if len(items) == 1 else ", ".join(items[:-1]) + f" {and_word} " + items[-1]


def _checklist(items: list[str]) -> str:
    return "\n".join(f"- [ ] {i}" for i in items)


def _numbered(steps: list[str]) -> str:
    return "\n".join(f"{n}. {s}" for n, s in enumerate(steps, 1))


# =============================================================================
# Language packs.
#
# when:        (phrase used inside the sentence, date placeholder, "HH:mm")
# actions:     (oblique form used inside "remind me to …", task title)
# events:      (phrase used inside the sentence, event title)  {person} allowed
# dishes:      (dish name, [ingredients], [cooking steps])
# notes_text:  (user template with {value}, note title)
# chat:        (user text, reply)
# currencies:  (localized name, ISO code)
# Templates use {slots}; replies are written in the same language.
# =============================================================================

EN = {
    "and": "and",
    "items": ["milk", "eggs", "bread", "butter", "rice", "sugar", "coffee", "tea", "onions", "tomatoes",
              "potatoes", "apples", "bananas", "yogurt", "cheese", "pasta", "flour", "salt", "honey",
              "shampoo", "toothpaste", "detergent", "notebooks", "batteries"],
    "list_titles": ["Groceries", "Shopping list", "Market list", "Weekend shopping", "Pantry restock", "Essentials"],
    "people": ["Ravi", "Priya", "Amit", "Neha", "Sarah", "John", "Kiran", "Fatima"],
    "when": [("tomorrow morning", "{tomorrow}", "09:00"), ("tomorrow afternoon", "{tomorrow}", "14:00"),
             ("tomorrow evening", "{tomorrow}", "18:00"), ("tomm evening", "{tomorrow}", "18:00"),
             ("tmrw night", "{tomorrow}", "20:00"), ("day after tomorrow at 10am", "{day_after}", "10:00"),
             ("on Friday at 5pm", "{friday}", "17:00"), ("next Monday at 3pm", "{monday}", "15:00"),
             ("Saturday morning", "{saturday}", "09:00"), ("tonight", "{today}", "20:00")],
    "actions": [("take my medicine", "Take medicine"), ("call the plumber", "Call the plumber"),
                ("pay the electricity bill", "Pay electricity bill"), ("water the plants", "Water the plants"),
                ("call mom", "Call mom"), ("renew the gym membership", "Renew gym membership"),
                ("submit the assignment", "Submit the assignment"), ("pick up the laundry", "Pick up laundry"),
                ("book train tickets", "Book train tickets"), ("take out the trash", "Take out the trash")],
    "t_task": ["remind me {when} to {action}", "set a reminder {when} to {action}",
               "{when} remind me to {action}", "add a task {when} — {action}",
               "dont let me forget to {action} {when}"],
    "r_task": ["Reminder set for {when}.", "Done — I'll remind you {when}.", "Got it, reminder added for {when}."],
    "events": [("a dentist appointment", "Dentist appointment"), ("a team meeting", "Team meeting"),
               ("a doctor's appointment", "Doctor's appointment"), ("a call with {person}", "Call with {person}"),
               ("lunch with {person}", "Lunch with {person}"), ("a parent-teacher meeting", "Parent-teacher meeting")],
    "t_event": ["put {event} on my calendar {when}", "schedule {event} {when}",
                "add {event} to the calendar {when}", "I have {event} {when}, add it"],
    "r_event": ["Added to your calendar.", "Scheduled — it's on your calendar.", "Done, event added."],
    "t_note": ["make a checklist note called {title} with {items}", "save a {title} list: {items}",
               "add a note titled {title} with {items} as a checklist", "note down a {title} list — {items}",
               "create a shopping list called {title}: {items}"],
    "r_note": ["Saved your {title} checklist.", "Done — {title} list saved.", "Added the {title} note."],
    "t_note_task": ["save a {title} list with {items}, and remind me {when} to buy them",
                    "make a checklist called {title}: {items} — then remind me {when} to buy everything",
                    "note down {items} in a {title} list and set a reminder {when} to purchase them"],
    "r_note_task": ["Saved the {title} list and set your reminder.", "Checklist saved — I'll remind you {when}."],
    "buy_title": "Buy {title_lc}",
    "t_append": ["add {items} to my {title} list", "put {items} on the {title} note",
                 "append {items} to {title}"],
    "r_append": ["Added to your {title} list.", "Updated {title}."],
    "notes_text": [("note down the wifi password: {value}", "WiFi password"),
                   ("save my locker code {value} in a note", "Locker code"),
                   ("note the electricity meter reading: {value}", "Meter reading"),
                   ("keep my bike lock combo {value} in a note", "Bike lock combo")],
    "r_note_text": ["Saved it to a note.", "Noted — it's stored safely on your phone."],
    "t_recipe": ["search the process for making {dish} and then add the ingredients in the notes and then remind me {when} to purchase them",
                 "look up a {dish} recipe, save the ingredients as a checklist and remind me {when} to buy them",
                 "find how to make {dish}, note down what I need to buy and set a reminder {when} to get it all",
                 "search a good {dish} recipe and put the shopping list in my notes, remind me {when} to shop"],
    "r_recipe_r1": ["Let me look up the recipe first.", "Searching for the recipe — one moment."],
    "r_recipe_r2": ["Saved the {dish} ingredients as a checklist and set your reminder for {when}.",
                    "Done — shopping checklist saved and I'll remind you {when}."],
    "q_recipe": "{dish} recipe ingredients",
    "ing_title": "{dish} ingredients",
    "steps_head": "Method:",
    "dishes": [
        ("red sauce pasta", ["Pasta (400 g)", "Canned tomatoes (800 g)", "Garlic (4 cloves)", "Onion (1)", "Olive oil", "Basil", "Parmesan"],
         ["Boil pasta in salted water until al dente.", "Saute garlic and onion in olive oil, add tomatoes and simmer 15 min.", "Toss pasta with sauce, top with basil and parmesan."]),
        ("chicken biryani", ["Chicken (1 kg)", "Basmati rice (500 g)", "Yogurt (200 g)", "Onions (3)", "Biryani masala (3 tbsp)", "Saffron", "Mint & coriander", "Ghee (4 tbsp)"],
         ["Marinate chicken in yogurt and spices for 1 hour.", "Fry onions golden; par-boil the rice.", "Layer rice and chicken, dum cook 25 minutes."]),
        ("pancakes", ["Flour (2 cups)", "Milk (1.5 cups)", "Eggs (2)", "Baking powder (2 tsp)", "Sugar (2 tbsp)", "Butter", "Maple syrup"],
         ["Whisk dry ingredients, then mix in milk and eggs.", "Cook ladlefuls on a buttered pan until bubbles form, then flip.", "Serve with butter and maple syrup."]),
        ("greek salad", ["Cucumbers (2)", "Tomatoes (4)", "Red onion (1)", "Feta cheese (200 g)", "Olives", "Olive oil", "Oregano"],
         ["Chop cucumbers, tomatoes and onion into chunks.", "Add olives and feta.", "Dress with olive oil and oregano."]),
        ("vegetable stir fry", ["Broccoli (1 head)", "Bell peppers (2)", "Carrots (2)", "Soy sauce", "Garlic", "Ginger", "Sesame oil"],
         ["Chop all vegetables evenly.", "Stir-fry garlic and ginger, add vegetables on high heat.", "Finish with soy sauce and sesame oil."]),
        ("tomato soup", ["Tomatoes (1 kg)", "Onion (1)", "Garlic (3 cloves)", "Vegetable stock (500 ml)", "Cream (100 ml)", "Butter", "Black pepper"],
         ["Saute onion and garlic in butter, add chopped tomatoes.", "Add stock, simmer 20 min, then blend smooth.", "Stir in cream and season."]),
        ("chocolate brownies", ["Dark chocolate (200 g)", "Butter (150 g)", "Sugar (200 g)", "Eggs (3)", "Flour (100 g)", "Cocoa powder", "Walnuts"],
         ["Melt chocolate with butter; whisk in sugar and eggs.", "Fold in flour, cocoa and walnuts.", "Bake at 180°C for 25 minutes."]),
        ("hummus", ["Chickpeas (400 g)", "Tahini (3 tbsp)", "Lemon (1)", "Garlic (2 cloves)", "Olive oil", "Cumin", "Paprika"],
         ["Blend chickpeas, tahini, lemon juice and garlic.", "Stream in olive oil until creamy.", "Top with cumin, paprika and olive oil."]),
        ("fried rice", ["Cooked rice (3 cups)", "Eggs (2)", "Spring onions", "Peas (1 cup)", "Carrots (1)", "Soy sauce", "Sesame oil"],
         ["Scramble eggs and set aside.", "Stir-fry vegetables, add rice on high heat.", "Add soy sauce, eggs and sesame oil, toss well."]),
        ("masala chai", ["Milk (500 ml)", "Black tea (3 tsp)", "Ginger", "Cardamom (4 pods)", "Cinnamon", "Cloves", "Sugar"],
         ["Crush the spices and boil with water.", "Add tea leaves and milk, simmer 5 minutes.", "Strain, sweeten and serve hot."]),
    ],
    "currencies": [("dollars", "USD"), ("US dollars", "USD"), ("rupees", "INR"), ("euros", "EUR"),
                   ("pounds", "GBP"), ("yen", "JPY"), ("dirhams", "AED"), ("Singapore dollars", "SGD")],
    "t_rate": ["what's the {a} to {b} rate?", "how much is one {a_s} in {b}?", "check the {a} {b} exchange rate"],
    "r_rate": ["Checking the {A} to {B} rate.", "Fetching {A}→{B}."],
    "t_convert": ["convert {n} {a} to {b}", "what's {n} {a} in {b}?", "how much is {n} {a} in {b}"],
    "r_convert": ["Converting {n} {A} to {B}.", "Let me convert that for you."],
    "t_rate_alert": ["alert me when 1 {a_s} crosses {n} {b}", "tell me when 1 {a_s} hits {n} {b}",
                     "notify me if the {a} to {b} rate passes {n}", "let me know when 1 {a_s} goes past {n} {b}"],
    "r_rate_alert": ["Alert set — I'll tell you when it crosses {n} {B}.", "Done, watching {A}→{B} for {n}."],
    "t_trash": ["how many screenshots are in my trash?", "check my screenshot trash", "what's sitting in the screenshot trash?"],
    "r_trash": ["Let me check your screenshot trash.", "Checking the trash now."],
    "ss_finds": [("the wifi password", "wifi password", "wifi password", "cafe-guest-8842"),
                 ("the OTP", "OTP", "OTP", "418302"),
                 ("that address", "address", "address", "42 Park Street, Kolkata 700016"),
                 ("the locker code", "locker code", "locker code", "77-15-38")],
    "t_search_ss": ["what was {thing} I screenshotted?", "find {thing} in my screenshots",
                    "search my screenshots for {thing}", "dig up {thing} from my screenshots"],
    "r_search_ss_r1": ["Let me search your screenshots.", "Checking your screenshots."],
    "r_search_ss": ["Your {label} is {value}.", "From your screenshot — {label}: {value}."],
    "days": [("tomorrow", "{tomorrow}"), ("on Friday", "{friday}"), ("on Monday", "{monday}"),
             ("on Saturday", "{saturday}"), ("today", "{today}")],
    "t_agenda": ["what's on my calendar {day}?", "what do I have {day}?", "any plans {day}?",
                 "list my tasks and events {day}"],
    "r_agenda_r1": ["Let me check your calendar.", "Checking your schedule."],
    "r_agenda": ["You have {n} things {day}: {list}.", "{day} you've got: {list}."],
    "r_agenda_empty": ["Nothing on your calendar {day} — the day is free.", "That day is clear, nothing scheduled."],
    "t_readnote": ["what's in my {title} list?", "read my {title} note", "what did I put on the {title} list?"],
    "r_readnote_r1": ["Let me open that note.", "Reading your note."],
    "r_readnote": ["Your {title} list has: {list}.", "On {title} you have: {list}."],
    "t_readnote_act": ["read my {title} list and remind me {when} to buy everything on it",
                       "check what's on my {title} note and set a reminder {when} to get it all"],
    "r_readnote_act": ["Reminder set — I'll remind you {when} to buy everything on {title}."],
    "spends": [("lunch", "food", "Lunch"), ("coffee", "food", "Coffee"), ("an auto ride", "transport", "Auto ride"),
               ("petrol", "transport", "Petrol"), ("groceries", "groceries", "Groceries"),
               ("the electricity bill", "bills", "Electricity bill"), ("a mobile recharge", "bills", "Mobile recharge"),
               ("new shoes", "shopping", "Shoes"), ("medicines", "health", "Medicines"),
               ("movie tickets", "entertainment", "Movie tickets")],
    "t_expense": ["spent {amt} on {thing}", "add an expense — {amt} for {thing}",
                  "log {amt} rupees for {thing}", "i paid {amt} for {thing}, track it"],
    "r_expense": ["Added ₹{amt} under {cat}.", "Logged — ₹{amt} for {cat}."],
    "periods": [("this month", "{this_month}"), ("last month", "{last_month}")],
    "t_spend": ["how much did I spend {period}?", "what's my total spending {period}?",
                "where did my money go {period}?"],
    "r_spend_r1": ["Let me check your spending.", "Pulling up your expenses."],
    "r_spend": ["You spent {total} {period}, mostly on {top}.",
                "Your {period} total is {total} — {top} was the biggest chunk."],
    "cat_labels": {"food": "food", "transport": "transport", "bills": "bills", "groceries": "groceries", "shopping": "shopping"},
    "chat": [("hi", "Hi! I can add notes, set reminders, put events on your calendar, check exchange rates and search the web. What do you need?"),
             ("good morning", "Good morning! What can I do for you today?"),
             ("thanks!", "You're welcome! Anything else?"),
             ("thank you so much", "Happy to help! Ping me anytime."),
             ("what can you do?", "I can save notes and checklists, set task reminders, add calendar events, check your screenshot trash, convert currencies, and search the web for things I don't know."),
             ("who painted the Mona Lisa?", "Leonardo da Vinci painted the Mona Lisa, in the early 1500s."),
             ("what's the capital of Australia?", "Canberra is the capital of Australia."),
             ("delete my groceries note", "I can't delete notes yet — you can remove it from the Notes screen. I can add or update notes for you."),
             ("cancel all my reminders", "I can't remove reminders yet — open Calendar to manage them. I can add new tasks or events for you.")],
}

HI = {
    "and": "और",
    "items": ["दूध", "अंडे", "ब्रेड", "मक्खन", "चावल", "चीनी", "कॉफी", "चायपत्ती", "प्याज़", "टमाटर",
              "आलू", "सेब", "केले", "दही", "पनीर", "आटा", "नमक", "शहद", "शैम्पू", "टूथपेस्ट"],
    "list_titles": ["ग्रोसरी", "सब्ज़ी की लिस्ट", "बाज़ार की लिस्ट", "राशन", "हफ्ते की खरीदारी"],
    "people": ["रवि", "प्रिया", "अमित", "नेहा", "किरण", "सान्या"],
    "when": [("कल सुबह", "{tomorrow}", "09:00"), ("कल दोपहर", "{tomorrow}", "14:00"),
             ("कल शाम", "{tomorrow}", "18:00"), ("कल रात", "{tomorrow}", "20:00"),
             ("परसों सुबह", "{day_after}", "09:00"), ("शुक्रवार शाम 5 बजे", "{friday}", "17:00"),
             ("सोमवार सुबह", "{monday}", "09:00"), ("शनिवार सुबह", "{saturday}", "09:00"),
             ("आज रात", "{today}", "20:00")],
    "actions": [("दवाई लेने", "दवाई लेना"), ("माँ को फोन करने", "माँ को फोन करना"),
                ("बिजली का बिल भरने", "बिजली का बिल भरना"), ("पौधों को पानी देने", "पौधों को पानी देना"),
                ("प्लंबर को बुलाने", "प्लंबर को बुलाना"), ("कपड़े प्रेस से लाने", "कपड़े प्रेस से लाना"),
                ("ट्रेन की टिकट बुक करने", "ट्रेन की टिकट बुक करना"), ("असाइनमेंट जमा करने", "असाइनमेंट जमा करना")],
    "t_task": ["मुझे {when} {action} की याद दिलाओ", "{when} {action} की याद दिलाना",
               "{when} का रिमाइंडर लगा दो — {action_ti}", "याद दिलाना, {when} {action} है"],
    "r_task": ["{when} के लिए रिमाइंडर सेट कर दिया।", "ठीक है, {when} याद दिला दूँगा।"],
    "events": [("डेंटिस्ट की अपॉइंटमेंट", "डेंटिस्ट की अपॉइंटमेंट"), ("टीम मीटिंग", "टीम मीटिंग"),
               ("डॉक्टर की अपॉइंटमेंट", "डॉक्टर की अपॉइंटमेंट"), ("{person} के साथ कॉल", "{person} के साथ कॉल"),
               ("{person} के साथ लंच", "{person} के साथ लंच")],
    "t_event": ["{when} {event} कैलेंडर में डाल दो", "{when} {event} शेड्यूल कर दो",
                "{when} {event} है, कैलेंडर में जोड़ दो"],
    "r_event": ["कैलेंडर में जोड़ दिया।", "इवेंट शेड्यूल कर दिया।"],
    "t_note": ["{title} की लिस्ट बनाओ: {items}", "एक नोट में {items} लिख लो, टाइटल {title} रखना",
               "{items} — इनकी चेकलिस्ट बना दो, नाम {title}", "नोट बनाओ {title} के नाम से जिसमें {items} हों"],
    "r_note": ["{title} की लिस्ट सेव कर दी।", "चेकलिस्ट बन गई — {title}।"],
    "t_note_task": ["{title} की लिस्ट बनाओ ({items}) और {when} खरीदने की याद दिलाना",
                    "{items} की चेकलिस्ट बनाओ, टाइटल {title}, और {when} याद दिलाओ कि सामान लेना है"],
    "r_note_task": ["{title} की लिस्ट सेव कर दी और {when} का रिमाइंडर लगा दिया।"],
    "buy_title": "{title} खरीदना",
    "t_append": ["मेरी {title} लिस्ट में {items} जोड़ दो", "{title} वाले नोट में {items} भी डाल दो"],
    "r_append": ["{title} लिस्ट में जोड़ दिया।"],
    "notes_text": [("वाईफाई पासवर्ड नोट कर लो: {value}", "वाईफाई पासवर्ड"),
                   ("मेरा लॉकर कोड {value} नोट कर लो", "लॉकर कोड"),
                   ("बिजली मीटर की रीडिंग नोट करो: {value}", "मीटर रीडिंग")],
    "r_note_text": ["नोट में सेव कर दिया।"],
    "t_recipe": ["{dish} बनाने की विधि खोजो, फिर सामग्री नोट्स में चेकलिस्ट बनाकर डालो और {when} खरीदने की याद दिलाना",
                 "{dish} की रेसिपी ढूँढो और जो सामान चाहिए उसकी लिस्ट सेव करो, {when} याद दिलाना कि खरीदना है",
                 "{dish} कैसे बनता है सर्च करो, सामग्री की चेकलिस्ट बनाओ और {when} का रिमाइंडर लगाओ"],
    "r_recipe_r1": ["पहले रेसिपी देख लेता हूँ।", "रेसिपी खोज रहा हूँ, एक मिनट।"],
    "r_recipe_r2": ["{dish} की सामग्री चेकलिस्ट में सेव कर दी और {when} का रिमाइंडर लगा दिया।"],
    "q_recipe": "{dish} रेसिपी सामग्री",
    "ing_title": "{dish} सामग्री",
    "steps_head": "विधि:",
    "dishes": [
        ("पनीर बटर मसाला", ["पनीर (400 ग्राम)", "मक्खन", "टमाटर (6)", "क्रीम (100 मिली)", "अदरक-लहसुन पेस्ट", "गरम मसाला", "कसूरी मेथी"],
         ["टमाटर, अदरक-लहसुन भूनकर प्यूरी बनाएं।", "मक्खन में प्यूरी और मसाले पकाएं।", "पनीर और क्रीम डालकर 5 मिनट पकाएं।"]),
        ("आलू पराठा", ["गेहूं का आटा (2 कप)", "आलू (4)", "हरी मिर्च", "धनिया", "मक्खन", "नमक"],
         ["आलू उबालकर मसाले के साथ मैश करें।", "आटे की लोई में भरावन भरें और बेलें।", "तवे पर मक्खन लगाकर सुनहरा सेकें।"]),
        ("वेज पुलाव", ["बासमती चावल (2 कप)", "मटर (1 कप)", "गाजर (2)", "बीन्स", "प्याज़ (2)", "साबुत मसाले", "घी"],
         ["घी में साबुत मसाले और प्याज़ भूनें।", "सब्ज़ियाँ और भीगे चावल डालें।", "4 कप पानी डालकर दम पर पकाएं।"]),
        ("दाल मखनी", ["काली उड़द दाल (1 कप)", "राजमा (1/4 कप)", "मक्खन", "क्रीम", "टमाटर प्यूरी", "अदरक-लहसुन पेस्ट"],
         ["दाल-राजमा रातभर भिगोकर उबालें।", "मक्खन में प्यूरी और मसाले पकाएं, दाल मिलाएं।", "धीमी आंच पर 30 मिनट पकाकर क्रीम डालें।"]),
        ("छोले", ["काबुली चने (2 कप)", "प्याज़ (2)", "टमाटर (3)", "छोले मसाला", "अदरक", "हरी मिर्च"],
         ["चने रातभर भिगोकर उबाल लें।", "प्याज़-टमाटर का मसाला भूनें।", "चने मिलाकर 15 मिनट पकाएं।"]),
    ],
    "currencies": [("डॉलर", "USD"), ("रुपये", "INR"), ("यूरो", "EUR"), ("पाउंड", "GBP"), ("येन", "JPY"), ("दिरहम", "AED")],
    "t_rate": ["{a} से {b} का रेट क्या है?", "{a} का {b} में क्या भाव चल रहा है?", "एक {a_s} में कितने {b} मिलते हैं?"],
    "r_rate": ["{A} से {B} का रेट देख रहा हूँ।"],
    "t_convert": ["{n} {a} कितने {b} होंगे?", "{n} {a} को {b} में बदलो"],
    "r_convert": ["{n} {A} को {B} में बदल रहा हूँ।"],
    "t_rate_alert": ["जब 1 {a_s} {n} {b} पार कर जाए तो बताना", "{a} का {b} रेट {n} पहुँचे तो अलर्ट करना",
                     "मुझे बताना जब 1 {a_s} {n} {b} से ऊपर जाए"],
    "r_rate_alert": ["अलर्ट सेट कर दिया — {n} {B} पार होते ही बता दूँगा।", "ठीक है, {A}→{B} पर नज़र रखूँगा।"],
    "t_trash": ["मेरे ट्रैश में कितने स्क्रीनशॉट हैं?", "स्क्रीनशॉट ट्रैश चेक करो"],
    "r_trash": ["स्क्रीनशॉट ट्रैश चेक कर रहा हूँ।"],
    "ss_finds": [("वाईफाई पासवर्ड", "wifi password", "वाईफाई पासवर्ड", "cafe-guest-8842"),
                 ("ओटीपी", "OTP", "ओटीपी", "418302"),
                 ("वो पता", "address", "पता", "42 पार्क स्ट्रीट, कोलकाता 700016"),
                 ("लॉकर कोड", "locker code", "लॉकर कोड", "77-15-38")],
    "t_search_ss": ["मैंने जो {thing} स्क्रीनशॉट किया था वो क्या था?", "मेरे स्क्रीनशॉट में {thing} ढूँढो",
                    "स्क्रीनशॉट्स में {thing} सर्च करो"],
    "r_search_ss_r1": ["स्क्रीनशॉट खोज रहा हूँ।", "स्क्रीनशॉट देख रहा हूँ।"],
    "r_search_ss": ["आपका {label} है: {value}।", "स्क्रीनशॉट से — {label}: {value}।"],
    "days": [("कल", "{tomorrow}"), ("शुक्रवार को", "{friday}"), ("सोमवार को", "{monday}"), ("आज", "{today}")],
    "t_agenda": ["{day} मेरे कैलेंडर में क्या है?", "{day} क्या-क्या करना है?", "{day} के टास्क और इवेंट बताओ"],
    "r_agenda_r1": ["कैलेंडर देख रहा हूँ।"],
    "r_agenda": ["{day} {n} चीज़ें हैं: {list}।"],
    "r_agenda_empty": ["{day} कैलेंडर खाली है — कुछ भी शेड्यूल नहीं है।"],
    "t_readnote": ["मेरी {title} लिस्ट में क्या है?", "{title} वाला नोट पढ़कर बताओ"],
    "r_readnote_r1": ["नोट खोल रहा हूँ।"],
    "r_readnote": ["आपकी {title} लिस्ट में है: {list}।"],
    "t_readnote_act": ["मेरी {title} लिस्ट देखो और {when} सब खरीदने की याद दिलाना"],
    "r_readnote_act": ["{when} का रिमाइंडर लगा दिया — {title} का सामान खरीदना है।"],
    "spends": [("चाय-नाश्ते", "food", "चाय-नाश्ता"), ("खाने", "food", "खाना"), ("ऑटो", "transport", "ऑटो"),
               ("पेट्रोल", "transport", "पेट्रोल"), ("सब्ज़ी", "groceries", "सब्ज़ी"),
               ("बिजली के बिल", "bills", "बिजली का बिल"), ("रिचार्ज", "bills", "मोबाइल रिचार्ज"),
               ("दवाई", "health", "दवाई"), ("मूवी टिकट", "entertainment", "मूवी टिकट")],
    "t_expense": ["{thing} पर {amt} रुपये खर्च हुए", "{amt} रुपये {thing} में लगे, नोट कर लो",
                  "{thing} का खर्चा जोड़ो — {amt} रुपये"],
    "r_expense": ["₹{amt} {cat} में जोड़ दिया।"],
    "periods": [("इस महीने", "{this_month}"), ("पिछले महीने", "{last_month}")],
    "t_spend": ["{period} कितना खर्च हुआ?", "{period} मैंने कितने पैसे खर्च किए?"],
    "r_spend_r1": ["खर्च देख रहा हूँ।"],
    "r_spend": ["{period} कुल {total} खर्च हुए, सबसे ज़्यादा {top} पर।"],
    "cat_labels": {"food": "खाने", "transport": "आने-जाने", "bills": "बिलों", "groceries": "किराने", "shopping": "खरीदारी"},
    "chat": [("नमस्ते", "नमस्ते! मैं नोट्स, रिमाइंडर, कैलेंडर इवेंट और करेंसी रेट में मदद कर सकता हूँ। बताइए क्या करना है?"),
             ("शुक्रिया", "कोई बात नहीं! और कुछ चाहिए तो बताइए।"),
             ("तुम क्या कर सकते हो?", "मैं नोट्स और चेकलिस्ट सेव कर सकता हूँ, रिमाइंडर और कैलेंडर इवेंट लगा सकता हूँ, करेंसी रेट बता सकता हूँ और वेब पर खोज सकता हूँ।"),
             ("ताजमहल किसने बनवाया?", "ताजमहल शाहजहाँ ने अपनी पत्नी मुमताज़ महल की याद में बनवाया था।"),
             ("मेरा ग्रोसरी नोट डिलीट कर दो", "अभी मैं नोट डिलीट नहीं कर सकता — Notes स्क्रीन से हटा सकते हैं। मैं नोट जोड़ या अपडेट कर सकता हूँ।")],
}

BN = {
    "and": "আর",
    "items": ["চাল", "ডাল", "তেল", "দুধ", "ডিম", "পাউরুটি", "চিনি", "লবণ", "পেঁয়াজ", "টমেটো",
              "আলু", "আপেল", "কলা", "দই", "ময়দা", "চা পাতা", "সাবান", "টুথপেস্ট"],
    "list_titles": ["বাজারের তালিকা", "মুদির তালিকা", "সাপ্তাহিক বাজার", "রান্নাঘরের জিনিস"],
    "people": ["রাহুল", "প্রিয়া", "অমিত", "মৌসুমী", "সৌরভ"],
    "when": [("আগামীকাল সকালে", "{tomorrow}", "09:00"), ("আগামীকাল দুপুরে", "{tomorrow}", "14:00"),
             ("আগামীকাল সন্ধ্যায়", "{tomorrow}", "18:00"), ("আগামীকাল রাতে", "{tomorrow}", "20:00"),
             ("পরশু সকালে", "{day_after}", "09:00"), ("শুক্রবার বিকেল ৫টায়", "{friday}", "17:00"),
             ("সোমবার সকালে", "{monday}", "09:00"), ("আজ রাতে", "{today}", "20:00")],
    "actions": [("ওষুধ খাওয়ার কথা", "ওষুধ খাওয়া"), ("মাকে ফোন করার কথা", "মাকে ফোন করা"),
                ("বিদ্যুতের বিল দেওয়ার কথা", "বিদ্যুতের বিল দেওয়া"), ("গাছে জল দেওয়ার কথা", "গাছে জল দেওয়া"),
                ("ট্রেনের টিকিট কাটার কথা", "ট্রেনের টিকিট কাটা"), ("কাপড় ইস্ত্রি থেকে আনার কথা", "কাপড় ইস্ত্রি থেকে আনা")],
    "t_task": ["{when} আমাকে {action} মনে করিয়ে দিও", "{when} {action} মনে করিয়ে দিতে ভুলো না",
               "{when} রিমাইন্ডার দাও — {action_ti}"],
    "r_task": ["{when} মনে করিয়ে দেওয়ার রিমাইন্ডার সেট করেছি।", "ঠিক আছে, {when} মনে করিয়ে দেব।"],
    "events": [("ডাক্তারের অ্যাপয়েন্টমেন্ট", "ডাক্তারের অ্যাপয়েন্টমেন্ট"), ("টিম মিটিং", "টিম মিটিং"),
               ("{person}-এর সাথে কল", "{person}-এর সাথে কল"), ("ডেন্টিস্টের অ্যাপয়েন্টমেন্ট", "ডেন্টিস্টের অ্যাপয়েন্টমেন্ট")],
    "t_event": ["{when} {event} ক্যালেন্ডারে যোগ করো", "{when} {event} আছে, ক্যালেন্ডারে রাখো"],
    "r_event": ["ক্যালেন্ডারে যোগ করা হয়েছে।"],
    "t_note": ["{title} লিখে রাখো: {items}", "একটা নোটে {items} লিখে রাখো, নাম দাও {title}",
               "{items} — এগুলোর চেকলিস্ট বানাও, শিরোনাম {title}"],
    "r_note": ["{title} সেভ করা হয়েছে।", "চেকলিস্ট তৈরি — {title}।"],
    "t_note_task": ["{title} বানাও ({items}) আর {when} কেনার কথা মনে করিয়ে দিও",
                    "{items}-এর চেকলিস্ট বানাও, নাম {title}, আর {when} মনে করিয়ে দিও যে বাজার করতে হবে"],
    "r_note_task": ["{title} সেভ করা হয়েছে, {when} মনে করিয়ে দেব।"],
    "buy_title": "{title} কেনা",
    "t_append": ["আমার {title}-এ {items} যোগ করো", "{title} নোটে {items}-ও লিখে দাও"],
    "r_append": ["{title}-এ যোগ করা হয়েছে।"],
    "notes_text": [("ওয়াইফাই পাসওয়ার্ড লিখে রাখো: {value}", "ওয়াইফাই পাসওয়ার্ড"),
                   ("আমার লকারের কোড {value} নোট করো", "লকারের কোড")],
    "r_note_text": ["নোটে সেভ করা হয়েছে।"],
    "t_recipe": ["{dish} বানানোর পদ্ধতি খোঁজো, তারপর উপকরণগুলো নোটে চেকলিস্ট করে রাখো আর {when} কেনার কথা মনে করিয়ে দিও",
                 "{dish}-এর রেসিপি খুঁজে যা যা লাগবে তার তালিকা সেভ করো, {when} মনে করিয়ে দিও"],
    "r_recipe_r1": ["আগে রেসিপিটা দেখে নিই।", "রেসিপি খুঁজছি, এক মিনিট।"],
    "r_recipe_r2": ["{dish}-এর উপকরণ চেকলিস্টে সেভ করেছি, {when} কেনার কথা মনে করিয়ে দেব।"],
    "q_recipe": "{dish} রেসিপি উপকরণ",
    "ing_title": "{dish}-এর উপকরণ",
    "steps_head": "পদ্ধতি:",
    "dishes": [
        ("চিকেন কারি", ["মুরগির মাংস (১ কেজি)", "পেঁয়াজ (৩টা)", "টমেটো (২টা)", "আদা-রসুন বাটা", "গরম মসলা", "সর্ষের তেল", "টক দই"],
         ["মাংস দই আর মসলা দিয়ে মেখে রাখুন।", "পেঁয়াজ ভেজে আদা-রসুন আর টমেটো কষান।", "মাংস দিয়ে ঢেকে ৩০ মিনিট রান্না করুন।"]),
        ("খিচুড়ি", ["গোবিন্দভোগ চাল (২ কাপ)", "মুগ ডাল (১ কাপ)", "আলু (২টা)", "ফুলকপি", "মটরশুঁটি", "ঘি", "গরম মসলা"],
         ["ডাল শুকনো খোলায় ভেজে নিন।", "ঘিতে মসলা ফোড়ন দিয়ে সবজি ভাজুন।", "চাল-ডাল আর জল দিয়ে সেদ্ধ হওয়া পর্যন্ত রাঁধুন।"]),
        ("আলু পোস্ত", ["আলু (৪টা)", "পোস্ত বাটা (৪ টেবিল চামচ)", "কাঁচা লঙ্কা", "সর্ষের তেল", "কালো জিরে", "লবণ"],
         ["আলু ডুমো করে কেটে ভেজে নিন।", "পোস্ত বাটা আর লঙ্কা দিয়ে কষান।", "সামান্য জল দিয়ে মাখা মাখা করে নামান।"]),
        ("পায়েস", ["গোবিন্দভোগ চাল (আধ কাপ)", "দুধ (১ লিটার)", "চিনি", "এলাচ", "কাজু-কিশমিশ", "তেজপাতা"],
         ["দুধ ঘন করে ফুটিয়ে নিন।", "চাল দিয়ে নরম হওয়া পর্যন্ত রাঁধুন।", "চিনি, এলাচ আর কাজু-কিশমিশ দিয়ে নামান।"]),
    ],
    "currencies": [("ডলার", "USD"), ("টাকা", "INR"), ("ইউরো", "EUR"), ("পাউন্ড", "GBP"), ("ইয়েন", "JPY")],
    "t_rate": ["{a} থেকে {b}-র রেট কত?", "এক {a_s}-এ কত {b} পাওয়া যায়?"],
    "r_rate": ["{A} থেকে {B}-র রেট দেখছি।"],
    "t_convert": ["{n} {a} কত {b} হবে?", "{n} {a}-কে {b}-তে বদলাও"],
    "r_convert": ["{n} {A}-কে {B}-তে রূপান্তর করছি।"],
    "t_rate_alert": ["1 {a_s} যখন {n} {b} পার করবে তখন জানিও", "{a} থেকে {b} রেট {n} ছুঁলে অ্যালার্ট দিও",
                     "1 {a_s} {n} {b}-এর উপরে গেলে জানিও"],
    "r_rate_alert": ["অ্যালার্ট সেট — {n} {B} পার হলেই জানাব।", "ঠিক আছে, {A}→{B} নজরে রাখছি।"],
    "t_trash": ["আমার ট্র্যাশে কটা স্ক্রিনশট আছে?", "স্ক্রিনশট ট্র্যাশ চেক করো"],
    "r_trash": ["স্ক্রিনশট ট্র্যাশ চেক করছি।"],
    "ss_finds": [("ওয়াইফাই পাসওয়ার্ড", "wifi password", "ওয়াইফাই পাসওয়ার্ড", "cafe-guest-8842"),
                 ("ওটিপি", "OTP", "ওটিপি", "418302"),
                 ("সেই ঠিকানা", "address", "ঠিকানা", "42 Park Street, Kolkata 700016")],
    "t_search_ss": ["আমি যে {thing} স্ক্রিনশট নিয়েছিলাম সেটা কী ছিল?", "আমার স্ক্রিনশটে {thing} খোঁজো",
                    "স্ক্রিনশটে {thing} সার্চ করো"],
    "r_search_ss_r1": ["স্ক্রিনশট খুঁজছি।", "স্ক্রিনশট দেখছি।"],
    "r_search_ss": ["আপনার {label}: {value}।", "স্ক্রিনশট থেকে — {label}: {value}।"],
    "days": [("আগামীকাল", "{tomorrow}"), ("শুক্রবার", "{friday}"), ("সোমবার", "{monday}"), ("আজ", "{today}")],
    "t_agenda": ["{day} আমার ক্যালেন্ডারে কী আছে?", "{day} কী কী করতে হবে?"],
    "r_agenda_r1": ["ক্যালেন্ডার দেখছি।"],
    "r_agenda": ["{day} {n}টা জিনিস আছে: {list}।"],
    "r_agenda_empty": ["{day} ক্যালেন্ডার ফাঁকা — কিছু নেই।"],
    "t_readnote": ["আমার {title}-এ কী আছে?", "{title} নোটটা পড়ে শোনাও"],
    "r_readnote_r1": ["নোটটা খুলছি।"],
    "r_readnote": ["আপনার {title}-এ আছে: {list}।"],
    "t_readnote_act": ["আমার {title} দেখে {when} সব কেনার কথা মনে করিয়ে দিও"],
    "r_readnote_act": ["{when} মনে করিয়ে দেব — {title}-এর সব কিনতে হবে।"],
    "spends": [("চায়ের", "food", "চা"), ("খাবারের", "food", "খাবার"), ("অটোর", "transport", "অটো ভাড়া"),
               ("বাজারের", "groceries", "বাজার"), ("বিদ্যুৎ বিলের", "bills", "বিদ্যুৎ বিল"),
               ("ওষুধের", "health", "ওষুধ")],
    "t_expense": ["{thing} জন্য {amt} টাকা খরচ হয়েছে", "{amt} টাকা {thing} খরচ লিখে রাখো"],
    "r_expense": ["₹{amt} {cat}-এ যোগ করা হয়েছে।"],
    "periods": [("এই মাসে", "{this_month}"), ("গত মাসে", "{last_month}")],
    "t_spend": ["{period} কত খরচ হয়েছে?", "{period} আমি কত টাকা খরচ করেছি?"],
    "r_spend_r1": ["খরচের হিসাব দেখছি।"],
    "r_spend": ["{period} মোট {total} খরচ হয়েছে, সবচেয়ে বেশি {top}-এ।"],
    "cat_labels": {"food": "খাবারে", "transport": "যাতায়াতে", "bills": "বিলে", "groceries": "বাজারে", "shopping": "কেনাকাটায়"},
    "chat": [("নমস্কার", "নমস্কার! আমি নোট, রিমাইন্ডার, ক্যালেন্ডার ইভেন্ট আর কারেন্সি রেটে সাহায্য করতে পারি। বলুন কী করতে হবে?"),
             ("ধন্যবাদ", "স্বাগতম! আর কিছু লাগলে বলবেন।"),
             ("তুমি কী কী করতে পারো?", "আমি নোট আর চেকলিস্ট সেভ করতে পারি, রিমাইন্ডার আর ক্যালেন্ডার ইভেন্ট দিতে পারি, কারেন্সি রেট জানাতে পারি আর ওয়েবে খুঁজতে পারি।"),
             ("আমার নোটটা ডিলিট করো", "এখনও আমি নোট ডিলিট করতে পারি না — Notes স্ক্রিন থেকে মুছতে পারবেন। আমি নোট যোগ বা আপডেট করতে পারি।")],
}

TA = {
    "and": "மற்றும்",
    "items": ["அரிசி", "பருப்பு", "எண்ணெய்", "பால்", "முட்டை", "ரொட்டி", "சர்க்கரை", "உப்பு", "வெங்காயம்",
              "தக்காளி", "உருளைக்கிழங்கு", "ஆப்பிள்", "வாழைப்பழம்", "தயிர்", "மாவு", "தேயிலை", "சோப்பு"],
    "list_titles": ["மளிகை பட்டியல்", "சந்தை பட்டியல்", "வார பட்டியல்", "சமையலறை பொருட்கள்"],
    "people": ["ரவி", "பிரியா", "அருண்", "தீபா", "கார்த்திக்"],
    "when": [("நாளை காலை", "{tomorrow}", "09:00"), ("நாளை மதியம்", "{tomorrow}", "14:00"),
             ("நாளை மாலை", "{tomorrow}", "18:00"), ("நாளை இரவு", "{tomorrow}", "20:00"),
             ("நாளை மறுநாள் காலை", "{day_after}", "09:00"), ("வெள்ளிக்கிழமை மாலை 5 மணிக்கு", "{friday}", "17:00"),
             ("திங்கள் காலை", "{monday}", "09:00"), ("இன்று இரவு", "{today}", "20:00")],
    "actions": [("மருந்து எடுக்க", "மருந்து எடுக்க"), ("அம்மாவுக்கு போன் செய்ய", "அம்மாவுக்கு போன் செய்ய"),
                ("மின்சார பில் கட்ட", "மின்சார பில் கட்ட"), ("செடிகளுக்கு தண்ணீர் ஊற்ற", "செடிகளுக்கு தண்ணீர் ஊற்ற"),
                ("ரயில் டிக்கெட் புக் செய்ய", "ரயில் டிக்கெட் புக் செய்ய")],
    "t_task": ["{when} {action} நினைவூட்டு", "{when} எனக்கு {action} ஞாபகப்படுத்து",
               "{when} ரிமைண்டர் வை — {action_ti}"],
    "r_task": ["சரி, {when} நினைவூட்டுகிறேன்.", "{when} நினைவூட்டல் வைத்தேன்."],
    "events": [("பல் மருத்துவர் அப்பாய்ண்ட்மெண்ட்", "பல் மருத்துவர் அப்பாய்ண்ட்மெண்ட்"), ("டீம் மீட்டிங்", "டீம் மீட்டிங்"),
               ("{person}-உடன் கால்", "{person}-உடன் கால்"), ("மருத்துவர் அப்பாய்ண்ட்மெண்ட்", "மருத்துவர் அப்பாய்ண்ட்மெண்ட்")],
    "t_event": ["{when} {event} காலெண்டரில் சேர்", "{when} {event} இருக்கு, காலெண்டரில் போடு"],
    "r_event": ["காலெண்டரில் சேர்க்கப்பட்டது."],
    "t_note": ["{title} சேமி: {items}", "ஒரு குறிப்பில் {items} எழுது, தலைப்பு {title}",
               "{items} — இவற்றின் செக்லிஸ்ட் உருவாக்கு, பெயர் {title}"],
    "r_note": ["{title} சேமிக்கப்பட்டது.", "செக்லிஸ்ட் தயார் — {title}."],
    "t_note_task": ["{title} உருவாக்கு ({items}), {when} வாங்க நினைவூட்டு",
                    "{items} பட்டியலிடு, தலைப்பு {title}, {when} சாமான் வாங்க ஞாபகப்படுத்து"],
    "r_note_task": ["{title} சேமித்தேன், {when} நினைவூட்டுகிறேன்."],
    "buy_title": "{title} வாங்க",
    "t_append": ["என் {title}ல் {items} சேர்", "{title} குறிப்பில் {items}யும் எழுது"],
    "r_append": ["{title}ல் சேர்க்கப்பட்டது."],
    "notes_text": [("வைஃபை பாஸ்வேர்டை குறித்து வை: {value}", "வைஃபை பாஸ்வேர்டு"),
                   ("என் லாக்கர் கோடு {value} குறித்து வை", "லாக்கர் கோடு")],
    "r_note_text": ["குறிப்பில் சேமிக்கப்பட்டது."],
    "t_recipe": ["{dish} செய்முறையை தேடு, தேவையான பொருட்களை செக்லிஸ்டாக குறிப்பில் சேமி, {when} வாங்க நினைவூட்டு",
                 "{dish} ரெசிபி தேடி என்ன வாங்கணும்னு பட்டியல் போடு, {when} ஞாபகப்படுத்து"],
    "r_recipe_r1": ["முதலில் ரெசிபியை பார்க்கிறேன்.", "ரெசிபி தேடுகிறேன், ஒரு நிமிடம்."],
    "r_recipe_r2": ["{dish} பொருட்களை செக்லிஸ்டாக சேமித்தேன், {when} வாங்க நினைவூட்டுகிறேன்."],
    "q_recipe": "{dish} செய்முறை பொருட்கள்",
    "ing_title": "{dish} பொருட்கள்",
    "steps_head": "செய்முறை:",
    "dishes": [
        ("சாம்பார்", ["துவரம் பருப்பு (1 கப்)", "சாம்பார் பொடி (3 டீஸ்பூன்)", "புளி", "முருங்கைக்காய்", "வெங்காயம்", "தக்காளி", "கடுகு"],
         ["பருப்பை வேக வைக்கவும்.", "புளி கரைசலில் காய்கறிகளும் பொடியும் சேர்த்து கொதிக்க விடவும்.", "பருப்பு சேர்த்து தாளிக்கவும்."]),
        ("தக்காளி சாதம்", ["சாதம் (3 கப்)", "தக்காளி (4)", "வெங்காயம் (1)", "பச்சை மிளகாய்", "கடுகு", "கறிவேப்பிலை", "நல்லெண்ணெய்"],
         ["எண்ணெயில் கடுகு, வெங்காயம் தாளிக்கவும்.", "தக்காளி சேர்த்து குழையும் வரை வதக்கவும்.", "சாதத்தை சேர்த்து கிளறவும்."]),
        ("ரசம்", ["புளி", "தக்காளி (2)", "ரசப்பொடி (2 டீஸ்பூன்)", "பூண்டு", "மிளகு-சீரகம்", "கொத்தமல்லி", "கடுகு"],
         ["புளி கரைசலில் தக்காளியும் பொடியும் சேர்க்கவும்.", "நுரைக்கும் வரை கொதிக்க விடவும்.", "நெய்யில் தாளித்து கொத்தமல்லி தூவவும்."]),
        ("பொங்கல்", ["பச்சரிசி (1 கப்)", "பாசிப்பருப்பு (1/2 கப்)", "மிளகு", "சீரகம்", "இஞ்சி", "முந்திரி", "நெய்"],
         ["அரிசியும் பருப்பும் சேர்த்து குக்கரில் வேக வைக்கவும்.", "நெய்யில் மிளகு, சீரகம், முந்திரி தாளிக்கவும்.", "எல்லாம் சேர்த்து கிளறவும்."]),
    ],
    "currencies": [("டாலர்", "USD"), ("ரூபாய்", "INR"), ("யூரோ", "EUR"), ("பவுண்டு", "GBP"), ("யென்", "JPY")],
    "t_rate": ["{a} to {b} ரேட் என்ன?", "ஒரு {a_s} எத்தனை {b}?"],
    "r_rate": ["{A} - {B} ரேட்டை பார்க்கிறேன்."],
    "t_convert": ["{n} {a} எத்தனை {b}?", "{n} {a}-ஐ {b}-ஆக மாற்று"],
    "r_convert": ["{n} {A}-ஐ {B}-ஆக மாற்றுகிறேன்."],
    "t_rate_alert": ["1 {a_s} {n} {b} தாண்டினா சொல்லு", "{a} to {b} ரேட் {n} தொட்டா அலர்ட் பண்ணு",
                     "1 {a_s} {n} {b}-ஐ தாண்டினா தெரியப்படுத்து"],
    "r_rate_alert": ["அலர்ட் செட் — {n} {B} தாண்டினா சொல்றேன்.", "சரி, {A}→{B} கவனிக்கிறேன்."],
    "t_trash": ["என் ட்ராஷில் எத்தனை ஸ்கிரீன்ஷாட் இருக்கு?", "ஸ்கிரீன்ஷாட் ட்ராஷை செக் பண்ணு"],
    "r_trash": ["ஸ்கிரீன்ஷாட் ட்ராஷை பார்க்கிறேன்."],
    "ss_finds": [("வைஃபை பாஸ்வேர்டு", "wifi password", "வைஃபை பாஸ்வேர்டு", "cafe-guest-8842"),
                 ("ஓடிபி", "OTP", "ஓடிபி", "418302"),
                 ("அந்த முகவரி", "address", "முகவரி", "42 Park Street, Chennai 600001")],
    "t_search_ss": ["நான் ஸ்கிரீன்ஷாட் எடுத்த {thing} என்ன?", "என் ஸ்கிரீன்ஷாட்டில் {thing} தேடு",
                    "ஸ்கிரீன்ஷாட்டில் {thing} சர்ச் பண்ணு"],
    "r_search_ss_r1": ["ஸ்கிரீன்ஷாட்டை தேடுகிறேன்.", "ஸ்கிரீன்ஷாட்டை பார்க்கிறேன்."],
    "r_search_ss": ["உங்கள் {label}: {value}.", "ஸ்கிரீன்ஷாட்டில் இருந்து — {label}: {value}."],
    "days": [("நாளை", "{tomorrow}"), ("வெள்ளிக்கிழமை", "{friday}"), ("திங்கள்", "{monday}"), ("இன்று", "{today}")],
    "t_agenda": ["{day} என் காலெண்டரில் என்ன இருக்கு?", "{day} என்ன என்ன செய்யணும்?"],
    "r_agenda_r1": ["காலெண்டரை பார்க்கிறேன்."],
    "r_agenda": ["{day} {n} விஷயங்கள் இருக்கு: {list}."],
    "r_agenda_empty": ["{day} காலெண்டர் காலியாக இருக்கு."],
    "t_readnote": ["என் {title}ல் என்ன இருக்கு?", "{title} குறிப்பை படித்து சொல்லு"],
    "r_readnote_r1": ["குறிப்பை திறக்கிறேன்."],
    "r_readnote": ["உங்கள் {title}ல்: {list}."],
    "t_readnote_act": ["என் {title}ஐ பார்த்து {when} எல்லாம் வாங்க நினைவூட்டு"],
    "r_readnote_act": ["{when} நினைவூட்டுகிறேன் — {title} பொருட்களை வாங்க வேண்டும்."],
    "spends": [("சாப்பாட்டுக்கு", "food", "சாப்பாடு"), ("டீக்கு", "food", "டீ"), ("ஆட்டோவுக்கு", "transport", "ஆட்டோ"),
               ("மளிகைக்கு", "groceries", "மளிகை"), ("கரெண்ட் பில்லுக்கு", "bills", "கரெண்ட் பில்"),
               ("மருந்துக்கு", "health", "மருந்து")],
    "t_expense": ["{thing} {amt} ரூபாய் செலவு ஆச்சு", "{amt} ரூபாய் {thing} செலவு எழுது"],
    "r_expense": ["₹{amt} {cat}ல் சேர்த்தேன்."],
    "periods": [("இந்த மாதம்", "{this_month}"), ("போன மாதம்", "{last_month}")],
    "t_spend": ["{period} எவ்வளவு செலவு ஆனது?", "{period} நான் எவ்வளவு செலவழித்தேன்?"],
    "r_spend_r1": ["செலவுகளை பார்க்கிறேன்."],
    "r_spend": ["{period} மொத்தம் {total} செலவு, அதிகம் {top}ல்."],
    "cat_labels": {"food": "உணவில்", "transport": "பயணத்தில்", "bills": "பில்களில்", "groceries": "மளிகையில்", "shopping": "ஷாப்பிங்கில்"},
    "chat": [("வணக்கம்", "வணக்கம்! குறிப்புகள், நினைவூட்டல்கள், காலெண்டர் நிகழ்வுகள், கரன்சி ரேட் — எதில் உதவட்டும்?"),
             ("நன்றி", "மகிழ்ச்சி! வேறு ஏதாவது வேண்டுமா?"),
             ("நீ என்னலாம் செய்வாய்?", "நான் குறிப்புகளும் செக்லிஸ்ட்களும் சேமிப்பேன், நினைவூட்டல்களும் காலெண்டர் நிகழ்வுகளும் வைப்பேன், கரன்சி ரேட் சொல்வேன், வெப்பிலும் தேடுவேன்."),
             ("என் குறிப்பை டெலீட் செய்", "இப்போது என்னால் குறிப்புகளை நீக்க முடியாது — Notes திரையில் நீக்கலாம். நான் சேர்க்கவோ புதுப்பிக்கவோ முடியும்.")],
}

TE = {
    "and": "మరియు",
    "items": ["బియ్యం", "పప్పు", "నూనె", "పాలు", "గుడ్లు", "బ్రెడ్", "పంచదార", "ఉప్పు", "ఉల్లిపాయలు",
              "టమాటాలు", "బంగాళదుంపలు", "ఆపిల్స్", "అరటిపండ్లు", "పెరుగు", "పిండి", "టీ పొడి", "సబ్బు"],
    "list_titles": ["కూరగాయల జాబితా", "కిరాణా జాబితా", "వారం సరుకులు", "వంటగది సామాను"],
    "people": ["రవి", "ప్రియ", "అర్జున్", "దీప", "కిరణ్"],
    "when": [("రేపు ఉదయం", "{tomorrow}", "09:00"), ("రేపు మధ్యాహ్నం", "{tomorrow}", "14:00"),
             ("రేపు సాయంత్రం", "{tomorrow}", "18:00"), ("రేపు రాత్రి", "{tomorrow}", "20:00"),
             ("ఎల్లుండి ఉదయం", "{day_after}", "09:00"), ("శుక్రవారం సాయంత్రం 5 గంటలకు", "{friday}", "17:00"),
             ("సోమవారం ఉదయం", "{monday}", "09:00"), ("ఈ రాత్రి", "{today}", "20:00")],
    "actions": [("మందు వేసుకోవాలని", "మందు వేసుకోవడం"), ("అమ్మకి ఫోన్ చేయాలని", "అమ్మకి ఫోన్ చేయడం"),
                ("కరెంటు బిల్లు కట్టాలని", "కరెంటు బిల్లు కట్టడం"), ("మొక్కలకి నీళ్లు పోయాలని", "మొక్కలకి నీళ్లు పోయడం"),
                ("రైలు టికెట్ బుక్ చేయాలని", "రైలు టికెట్ బుక్ చేయడం")],
    "t_task": ["{when} {action} గుర్తు చేయి", "{when} నాకు {action} గుర్తు చేయాలి",
               "{when} రిమైండర్ పెట్టు — {action_ti}"],
    "r_task": ["సరే, {when} గుర్తు చేస్తాను.", "{when} గుర్తు చేసేలా రిమైండర్ పెట్టాను."],
    "events": [("డెంటిస్ట్ అపాయింట్మెంట్", "డెంటిస్ట్ అపాయింట్మెంట్"), ("టీమ్ మీటింగ్", "టీమ్ మీటింగ్"),
               ("{person}తో కాల్", "{person}తో కాల్"), ("డాక్టర్ అపాయింట్మెంట్", "డాక్టర్ అపాయింట్మెంట్")],
    "t_event": ["{when} {event} క్యాలెండర్‌లో పెట్టు", "{when} {event} ఉంది, క్యాలెండర్‌లో యాడ్ చేయి"],
    "r_event": ["క్యాలెండర్‌లో యాడ్ చేశాను."],
    "t_note": ["{title} రాయి: {items}", "ఒక నోట్‌లో {items} రాసి పెట్టు, పేరు {title}",
               "{items} — వీటి చెక్‌లిస్ట్ చెయ్యి, పేరు {title}"],
    "r_note": ["{title} సేవ్ చేశాను.", "చెక్‌లిస్ట్ సిద్ధం — {title}."],
    "t_note_task": ["{title} చెయ్యి ({items}), {when} కొనాలని గుర్తు చేయి",
                    "{items} జాబితా రాయి, పేరు {title}, {when} సామాను తేవాలని గుర్తు చేయి"],
    "r_note_task": ["{title} సేవ్ చేశాను, {when} గుర్తు చేస్తాను."],
    "buy_title": "{title} కొనడం",
    "t_append": ["నా {title}లో {items} చేర్చు", "{title} నోట్‌లో {items} కూడా రాయి"],
    "r_append": ["{title}లో చేర్చాను."],
    "notes_text": [("వైఫై పాస్‌వర్డ్ నోట్ చేయి: {value}", "వైఫై పాస్‌వర్డ్"),
                   ("నా లాకర్ కోడ్ {value} నోట్ చేయి", "లాకర్ కోడ్")],
    "r_note_text": ["నోట్‌లో సేవ్ చేశాను."],
    "t_recipe": ["{dish} చేసే విధానం వెతికి, కావాల్సిన సామగ్రిని చెక్‌లిస్ట్‌గా నోట్స్‌లో పెట్టి, {when} కొనాలని గుర్తు చేయి",
                 "{dish} రెసిపీ వెతికి ఏమేం కొనాలో జాబితా సేవ్ చేయి, {when} గుర్తు చేయి"],
    "r_recipe_r1": ["ముందు రెసిపీ చూస్తాను.", "రెసిపీ వెతుకుతున్నాను, ఒక్క నిమిషం."],
    "r_recipe_r2": ["{dish} సామగ్రిని చెక్‌లిస్ట్‌గా సేవ్ చేశాను, {when} కొనాలని గుర్తు చేస్తాను."],
    "q_recipe": "{dish} రెసిపీ కావలసినవి",
    "ing_title": "{dish} సామగ్రి",
    "steps_head": "విధానం:",
    "dishes": [
        ("పులిహోర", ["అన్నం (3 కప్పులు)", "చింతపండు", "పల్లీలు", "ఎండుమిర్చి", "ఆవాలు", "పసుపు", "కరివేపాకు"],
         ["చింతపండు గుజ్జు తీసి ఉడికించాలి.", "నూనెలో పోపు వేసి గుజ్జు కలపాలి.", "అన్నంలో కలిపి పైన పల్లీలు చల్లాలి."]),
        ("సాంబార్", ["కందిపప్పు (1 కప్పు)", "సాంబార్ పొడి (3 స్పూన్లు)", "చింతపండు", "మునగకాడలు", "ఉల్లిపాయలు", "టమాటాలు", "ఆవాలు"],
         ["పప్పు మెత్తగా ఉడికించాలి.", "చింతపండు నీటిలో కూరగాయలు, పొడి వేసి మరిగించాలి.", "పప్పు కలిపి పోపు పెట్టాలి."]),
        ("టమాటా పప్పు", ["కందిపప్పు (1 కప్పు)", "టమాటాలు (4)", "పచ్చిమిర్చి", "వెల్లుల్లి", "ఆవాలు", "కరివేపాకు", "నెయ్యి"],
         ["పప్పు, టమాటాలు కలిపి ఉడికించాలి.", "మెత్తగా మెదిపి ఉప్పు కలపాలి.", "నెయ్యిలో పోపు వేసి పప్పులో కలపాలి."]),
        ("ఉప్మా", ["బొంబాయి రవ్వ (1 కప్పు)", "ఉల్లిపాయ (1)", "పచ్చిమిర్చి", "అల్లం", "ఆవాలు", "జీడిపప్పు", "నెయ్యి"],
         ["రవ్వను దోరగా వేయించాలి.", "పోపులో ఉల్లిపాయ, మిర్చి వేయించి నీళ్లు పోయాలి.", "రవ్వ కలిపి ఉడికించాలి."]),
    ],
    "currencies": [("డాలర్", "USD"), ("రూపాయి", "INR"), ("యూరో", "EUR"), ("పౌండ్", "GBP"), ("యెన్", "JPY")],
    "t_rate": ["{a} నుంచి {b} రేటు ఎంత?", "ఒక {a_s}కి ఎన్ని {b} వస్తాయి?"],
    "r_rate": ["{A} నుంచి {B} రేటు చూస్తున్నాను."],
    "t_convert": ["{n} {a} ఎన్ని {b} అవుతాయి?", "{n} {a}ని {b}లోకి మార్చు"],
    "r_convert": ["{n} {A}ని {B}లోకి మారుస్తున్నాను."],
    "t_rate_alert": ["1 {a_s} {n} {b} దాటితే చెప్పు", "{a} to {b} రేటు {n} తాకితే అలర్ట్ చేయి",
                     "1 {a_s} {n} {b} పైకి వెళ్తే తెలియజేయి"],
    "r_rate_alert": ["అలర్ట్ సెట్ — {n} {B} దాటగానే చెప్తాను.", "సరే, {A}→{B} గమనిస్తాను."],
    "t_trash": ["నా ట్రాష్‌లో ఎన్ని స్క్రీన్‌షాట్లు ఉన్నాయి?", "స్క్రీన్‌షాట్ ట్రాష్ చెక్ చేయి"],
    "r_trash": ["స్క్రీన్‌షాట్ ట్రాష్ చూస్తున్నాను."],
    "ss_finds": [("వైఫై పాస్‌వర్డ్", "wifi password", "వైఫై పాస్‌వర్డ్", "cafe-guest-8842"),
                 ("ఓటీపీ", "OTP", "ఓటీపీ", "418302"),
                 ("ఆ అడ్రస్", "address", "అడ్రస్", "42 Park Street, Hyderabad 500001")],
    "t_search_ss": ["నేను స్క్రీన్‌షాట్ తీసిన {thing} ఏంటి?", "నా స్క్రీన్‌షాట్లలో {thing} వెతుకు",
                    "స్క్రీన్‌షాట్లలో {thing} సర్చ్ చేయి"],
    "r_search_ss_r1": ["స్క్రీన్‌షాట్లు వెతుకుతున్నాను.", "స్క్రీన్‌షాట్లు చూస్తున్నాను."],
    "r_search_ss": ["మీ {label}: {value}.", "స్క్రీన్‌షాట్ నుంచి — {label}: {value}."],
    "days": [("రేపు", "{tomorrow}"), ("శుక్రవారం", "{friday}"), ("సోమవారం", "{monday}"), ("ఈరోజు", "{today}")],
    "t_agenda": ["{day} నా క్యాలెండర్‌లో ఏం ఉంది?", "{day} ఏం చేయాలి?"],
    "r_agenda_r1": ["క్యాలెండర్ చూస్తున్నాను."],
    "r_agenda": ["{day} {n} పనులు ఉన్నాయి: {list}."],
    "r_agenda_empty": ["{day} క్యాలెండర్ ఖాళీగా ఉంది."],
    "t_readnote": ["నా {title}లో ఏం ఉంది?", "{title} నోట్ చదివి చెప్పు"],
    "r_readnote_r1": ["నోట్ తెరుస్తున్నాను."],
    "r_readnote": ["మీ {title}లో ఉన్నవి: {list}."],
    "t_readnote_act": ["నా {title} చూసి {when} అన్నీ కొనాలని గుర్తు చేయి"],
    "r_readnote_act": ["{when} గుర్తు చేస్తాను — {title} సామాను కొనాలి."],
    "spends": [("భోజనానికి", "food", "భోజనం"), ("టీకి", "food", "టీ"), ("ఆటోకి", "transport", "ఆటో"),
               ("కిరాణాకి", "groceries", "కిరాణా"), ("కరెంటు బిల్లుకి", "bills", "కరెంటు బిల్లు"),
               ("మందులకి", "health", "మందులు")],
    "t_expense": ["{thing} {amt} రూపాయలు ఖర్చు అయ్యాయి", "{amt} రూపాయలు {thing} ఖర్చు రాయి"],
    "r_expense": ["₹{amt} {cat}లో నమోదు చేశాను."],
    "periods": [("ఈ నెల", "{this_month}"), ("పోయిన నెల", "{last_month}")],
    "t_spend": ["{period} ఎంత ఖర్చు అయింది?", "{period} నేను ఎంత ఖర్చు చేశాను?"],
    "r_spend_r1": ["ఖర్చులు చూస్తున్నాను."],
    "r_spend": ["{period} మొత్తం {total} ఖర్చు, ఎక్కువగా {top}."],
    "cat_labels": {"food": "ఆహారంపై", "transport": "ప్రయాణంపై", "bills": "బిల్లులపై", "groceries": "కిరాణాపై", "shopping": "షాపింగ్‌పై"},
    "chat": [("నమస్తే", "నమస్తే! నోట్స్, రిమైండర్లు, క్యాలెండర్ ఈవెంట్లు, కరెన్సీ రేట్లు — దేనిలో సహాయం కావాలి?"),
             ("ధన్యవాదాలు", "సంతోషం! ఇంకేమైనా కావాలా?"),
             ("నువ్వు ఏం చేయగలవు?", "నేను నోట్స్ మరియు చెక్‌లిస్ట్‌లు సేవ్ చేయగలను, రిమైండర్లు మరియు క్యాలెండర్ ఈవెంట్లు పెట్టగలను, కరెన్సీ రేట్లు చెప్పగలను, వెబ్‌లో వెతకగలను."),
             ("నా నోట్ డిలీట్ చేయి", "ప్రస్తుతం నేను నోట్స్ డిలీట్ చేయలేను — Notes స్క్రీన్ నుంచి తీసేయవచ్చు. నేను కొత్తవి రాయగలను, అప్డేట్ చేయగలను.")],
}

MR = {
    "and": "आणि",
    "items": ["तांदूळ", "डाळ", "तेल", "दूध", "अंडी", "ब्रेड", "साखर", "मीठ", "कांदे", "टोमॅटो",
              "बटाटे", "सफरचंद", "केळी", "दही", "पीठ", "चहा पावडर", "साबण"],
    "list_titles": ["किराणा यादी", "भाजी यादी", "आठवड्याची खरेदी", "स्वयंपाकघर सामान"],
    "people": ["रवी", "प्रिया", "अमित", "स्नेहा", "ओंकार"],
    "when": [("उद्या सकाळी", "{tomorrow}", "09:00"), ("उद्या दुपारी", "{tomorrow}", "14:00"),
             ("उद्या संध्याकाळी", "{tomorrow}", "18:00"), ("उद्या रात्री", "{tomorrow}", "20:00"),
             ("परवा सकाळी", "{day_after}", "09:00"), ("शुक्रवारी संध्याकाळी ५ वाजता", "{friday}", "17:00"),
             ("सोमवारी सकाळी", "{monday}", "09:00"), ("आज रात्री", "{today}", "20:00")],
    "actions": [("औषध घ्यायची", "औषध घेणे"), ("आईला फोन करायची", "आईला फोन करणे"),
                ("वीज बिल भरायची", "वीज बिल भरणे"), ("झाडांना पाणी द्यायची", "झाडांना पाणी देणे"),
                ("रेल्वे तिकीट काढायची", "रेल्वे तिकीट काढणे")],
    "t_task": ["{when} मला {action} आठवण कर", "{when} {action} आठवण करून दे",
               "{when} रिमाइंडर लाव — {action_ti}"],
    "r_task": ["ठीक आहे, {when} आठवण करून देईन.", "{when} आठवण करण्यासाठी रिमाइंडर लावला आहे."],
    "events": [("दातांच्या डॉक्टरची अपॉइंटमेंट", "दातांच्या डॉक्टरची अपॉइंटमेंट"), ("टीम मीटिंग", "टीम मीटिंग"),
               ("{person}सोबत कॉल", "{person}सोबत कॉल"), ("डॉक्टरची अपॉइंटमेंट", "डॉक्टरची अपॉइंटमेंट")],
    "t_event": ["{when} {event} कॅलेंडरमध्ये टाक", "{when} {event} आहे, कॅलेंडरमध्ये अॅड कर"],
    "r_event": ["कॅलेंडरमध्ये अॅड केले."],
    "t_note": ["{title} बनव: {items}", "एका नोटमध्ये {items} लिहून ठेव, नाव {title}",
               "{items} — यांची चेकलिस्ट कर, नाव {title}"],
    "r_note": ["{title} सेव्ह केली आहे.", "चेकलिस्ट तयार — {title}."],
    "t_note_task": ["{title} बनव ({items}) आणि {when} खरेदीची आठवण कर",
                    "{items}ची चेकलिस्ट कर, नाव {title}, आणि {when} सामान आणायची आठवण कर"],
    "r_note_task": ["{title} सेव्ह केली, {when} आठवण करून देईन."],
    "buy_title": "{title} खरेदी करणे",
    "t_append": ["माझ्या {title}त {items} टाक", "{title} नोटमध्ये {items}सुद्धा लिही"],
    "r_append": ["{title}त टाकले."],
    "notes_text": [("वायफाय पासवर्ड लिहून ठेव: {value}", "वायफाय पासवर्ड"),
                   ("माझा लॉकर कोड {value} नोट कर", "लॉकर कोड")],
    "r_note_text": ["नोटमध्ये सेव्ह केले."],
    "t_recipe": ["{dish} बनवण्याची कृती शोध, लागणारे सामान चेकलिस्ट करून नोट्समध्ये ठेव आणि {when} खरेदीची आठवण कर",
                 "{dish}ची रेसिपी शोधून काय काय आणायचे त्याची यादी सेव्ह कर, {when} आठवण कर"],
    "r_recipe_r1": ["आधी रेसिपी बघतो.", "रेसिपी शोधतोय, एक मिनिट."],
    "r_recipe_r2": ["{dish}चे सामान चेकलिस्टमध्ये सेव्ह केले, {when} खरेदीची आठवण करून देईन."],
    "q_recipe": "{dish} रेसिपी साहित्य",
    "ing_title": "{dish} साहित्य",
    "steps_head": "कृती:",
    "dishes": [
        ("पोहे", ["जाड पोहे (2 कप)", "कांदा (2)", "बटाटा (1)", "मोहरी", "हळद", "कढीपत्ता", "शेंगदाणे"],
         ["पोहे धुऊन निथळत ठेवा.", "फोडणीत कांदा-बटाटा परता.", "पोहे घालून वाफ काढा, लिंबू पिळा."]),
        ("साबुदाणा खिचडी", ["साबुदाणा (2 कप)", "बटाटा (2)", "शेंगदाण्याचा कूट (1 कप)", "हिरवी मिरची", "जिरे", "तूप", "मीठ"],
         ["साबुदाणा 4-5 तास भिजवा.", "तुपात जिरे-मिरची फोडणी करून बटाटा परता.", "साबुदाणा आणि कूट घालून वाफ काढा."]),
        ("मिसळ", ["मटकी (2 कप)", "कांदे (2)", "टोमॅटो (2)", "मिसळ मसाला", "फरसाण", "पाव", "कोथिंबीर"],
         ["मटकी मोड आणून उकडा.", "मसाला परतून रस्सा करा.", "फरसाण-कांदा घालून पावासोबत वाढा."]),
        ("पुरण पोळी", ["चणा डाळ (2 कप)", "गूळ (2 कप)", "गव्हाचे पीठ", "वेलची पूड", "जायफळ", "तूप"],
         ["डाळ शिजवून गुळासोबत पुरण करा.", "पिठाच्या लाटीत पुरण भरा.", "तव्यावर तुपावर खमंग भाजा."]),
    ],
    "currencies": [("डॉलर", "USD"), ("रुपया", "INR"), ("युरो", "EUR"), ("पाउंड", "GBP"), ("येन", "JPY")],
    "t_rate": ["{a} ते {b} रेट किती आहे?", "एका {a_s}ला किती {b} मिळतात?"],
    "r_rate": ["{A} ते {B} रेट बघतोय."],
    "t_convert": ["{n} {a} किती {b} होतील?", "{n} {a}चे {b}त रूपांतर कर"],
    "r_convert": ["{n} {A}चे {B}त रूपांतर करतोय."],
    "t_rate_alert": ["1 {a_s} {n} {b} ओलांडला की सांग", "{a} ते {b} रेट {n} झाला की अलर्ट कर",
                     "1 {a_s} {n} {b} च्या वर गेला की कळव"],
    "r_rate_alert": ["अलर्ट सेट — {n} {B} ओलांडताच सांगेन.", "ठीक आहे, {A}→{B} वर लक्ष ठेवतो."],
    "t_trash": ["माझ्या ट्रॅशमध्ये किती स्क्रीनशॉट आहेत?", "स्क्रीनशॉट ट्रॅश चेक कर"],
    "r_trash": ["स्क्रीनशॉट ट्रॅश बघतोय."],
    "ss_finds": [("वायफाय पासवर्ड", "wifi password", "वायफाय पासवर्ड", "cafe-guest-8842"),
                 ("ओटीपी", "OTP", "ओटीपी", "418302"),
                 ("तो पत्ता", "address", "पत्ता", "42 Park Street, Mumbai 400001")],
    "t_search_ss": ["मी स्क्रीनशॉट घेतलेला {thing} काय होता?", "माझ्या स्क्रीनशॉटमध्ये {thing} शोध",
                    "स्क्रीनशॉटमध्ये {thing} सर्च कर"],
    "r_search_ss_r1": ["स्क्रीनशॉट शोधतोय.", "स्क्रीनशॉट बघतोय."],
    "r_search_ss": ["तुमचा {label}: {value}.", "स्क्रीनशॉटमधून — {label}: {value}."],
    "days": [("उद्या", "{tomorrow}"), ("शुक्रवारी", "{friday}"), ("सोमवारी", "{monday}"), ("आज", "{today}")],
    "t_agenda": ["{day} माझ्या कॅलेंडरमध्ये काय आहे?", "{day} काय काय करायचे आहे?"],
    "r_agenda_r1": ["कॅलेंडर बघतोय."],
    "r_agenda": ["{day} {n} गोष्टी आहेत: {list}."],
    "r_agenda_empty": ["{day} कॅलेंडर रिकामे आहे."],
    "t_readnote": ["माझ्या {title}त काय आहे?", "{title} नोट वाचून सांग"],
    "r_readnote_r1": ["नोट उघडतोय."],
    "r_readnote": ["तुमच्या {title}त आहे: {list}."],
    "t_readnote_act": ["माझी {title} बघ आणि {when} सगळे आणायची आठवण कर"],
    "r_readnote_act": ["{when} आठवण करून देईन — {title}तील सामान आणायचे आहे."],
    "spends": [("जेवणावर", "food", "जेवण"), ("चहावर", "food", "चहा"), ("रिक्षावर", "transport", "रिक्षा"),
               ("किराण्यावर", "groceries", "किराणा"), ("वीज बिलावर", "bills", "वीज बिल"),
               ("औषधांवर", "health", "औषधे")],
    "t_expense": ["{thing} {amt} रुपये खर्च झाले", "{amt} रुपये {thing} खर्च लिहून ठेव"],
    "r_expense": ["₹{amt} {cat} नोंदवले."],
    "periods": [("या महिन्यात", "{this_month}"), ("गेल्या महिन्यात", "{last_month}")],
    "t_spend": ["{period} किती खर्च झाला?", "{period} मी किती पैसे खर्च केले?"],
    "r_spend_r1": ["खर्च बघतोय."],
    "r_spend": ["{period} एकूण {total} खर्च झाला, सर्वात जास्त {top}."],
    "cat_labels": {"food": "जेवणावर", "transport": "प्रवासावर", "bills": "बिलांवर", "groceries": "किराण्यावर", "shopping": "खरेदीवर"},
    "chat": [("नमस्कार", "नमस्कार! नोट्स, रिमाइंडर, कॅलेंडर इव्हेंट, करन्सी रेट — कशात मदत करू?"),
             ("धन्यवाद", "आनंदाने! आणखी काही हवे असल्यास सांगा."),
             ("तू काय काय करू शकतोस?", "मी नोट्स आणि चेकलिस्ट सेव्ह करू शकतो, रिमाइंडर आणि कॅलेंडर इव्हेंट लावू शकतो, करन्सी रेट सांगू शकतो आणि वेबवर शोधू शकतो."),
             ("माझी नोट डिलीट कर", "सध्या मी नोट्स डिलीट करू शकत नाही — Notes स्क्रीनवरून काढता येईल. मी नोट्स जोडू किंवा अपडेट करू शकतो.")],
}

PACKS = {"en": EN, "hi": HI, "bn": BN, "ta": TA, "te": TE, "mr": MR}

# Scenario weights (recipe/agenda/readnote/spend/search_ss count double: r1+r2 pairs).
WEIGHTS = [
    ("note_checklist", 12), ("note_text", 4), ("task", 13), ("event", 9),
    ("note_task", 8), ("recipe", 7), ("append", 5), ("rate", 5),
    ("convert", 5), ("trash", 3), ("chat", 7), ("agenda", 5), ("readnote", 5),
    ("expense", 8), ("spend", 4), ("search_ss", 4), ("rate_alert", 4),
]

AMOUNTS = [20, 45, 60, 80, 99, 120, 150, 199, 250, 300, 349, 450, 500, 650, 799, 1200, 2500]


def _group_indian(n: int) -> str:
    """12,34,567 — mirrors ExpenseMath.groupIndian so findings match the tool."""
    s = str(n)
    if len(s) <= 3:
        return s
    head, tail = s[:-3], s[-3:]
    parts = []
    while head:
        parts.insert(0, head[-2:])
        head = head[:-2]
    return ",".join(parts) + "," + tail

CONVERT_AMOUNTS = [20, 50, 75, 99, 100, 150, 250, 500, 1000, 2500]
RATE_THRESHOLDS = [75, 80, 85, 88, 90, 92, 95, 100, 110, 120, 1.1, 0.85]
NOTE_VALUES = ["quiver-guest-2027", "4517", "8829", "Kx7-2214", "meter 004312", "72-15-38", "flat-303-b"]


def _fill(rng, template: str, **slots) -> str:
    out = template
    for k, v in slots.items():
        out = out.replace("{" + k + "}", v)
    return out


def _pick_items(rng, pack, n=None) -> list[str]:
    return rng.sample(pack["items"], n or rng.choice([2, 3, 3, 4]))


def _build(rng, pack, kind):
    """Returns a list of seed-format rows for one scenario instance."""
    and_w = pack["and"]

    if kind == "note_checklist":
        items = _pick_items(rng, pack)
        title = rng.choice(pack["list_titles"])
        user = _fill(rng, rng.choice(pack["t_note"]), title=title, items=_join(items, and_w))
        reply = _fill(rng, rng.choice(pack["r_note"]), title=title)
        return [{"user": user, "output": {"steps": [
            {"tool": "add_note", "args": {"title": title, "body": _checklist(items)}}], "reply": reply}}]

    if kind == "note_text":
        tmpl, title = rng.choice(pack["notes_text"])
        value = rng.choice(NOTE_VALUES)
        user = _fill(rng, tmpl, value=value)
        return [{"user": user, "output": {"steps": [
            {"tool": "add_note", "args": {"title": title, "body": value}}], "reply": rng.choice(pack["r_note_text"])}}]

    if kind == "task":
        when_p, when_d, when_t = rng.choice(pack["when"])
        act_ob, act_ti = rng.choice(pack["actions"])
        user = _fill(rng, rng.choice(pack["t_task"]), when=when_p, action=act_ob, action_ti=act_ti)
        reply = _fill(rng, rng.choice(pack["r_task"]), when=when_p)
        return [{"user": user, "output": {"steps": [
            {"tool": "add_task", "args": {"title": act_ti, "date": when_d, "time": when_t}}], "reply": reply}}]

    if kind == "event":
        when_p, when_d, when_t = rng.choice(pack["when"])
        ev_phrase, ev_title = rng.choice(pack["events"])
        person = rng.choice(pack["people"])
        ev_phrase, ev_title = ev_phrase.replace("{person}", person), ev_title.replace("{person}", person)
        user = _fill(rng, rng.choice(pack["t_event"]), when=when_p, event=ev_phrase)
        return [{"user": user, "output": {"steps": [
            {"tool": "add_event", "args": {"title": ev_title, "date": when_d, "time": when_t}}],
            "reply": rng.choice(pack["r_event"])}}]

    if kind == "note_task":
        items = _pick_items(rng, pack)
        title = rng.choice(pack["list_titles"])
        when_p, when_d, when_t = rng.choice(pack["when"])
        user = _fill(rng, rng.choice(pack["t_note_task"]), title=title, items=_join(items, and_w), when=when_p)
        reply = _fill(rng, rng.choice(pack["r_note_task"]), title=title, when=when_p)
        buy = pack["buy_title"].replace("{title}", title).replace("{title_lc}", title.lower())
        return [{"user": user, "output": {"steps": [
            {"tool": "add_note", "args": {"title": title, "body": _checklist(items)}},
            {"tool": "add_task", "args": {"title": buy, "date": when_d, "time": when_t}}], "reply": reply}}]

    if kind == "recipe":
        dish, ingredients, steps = rng.choice(pack["dishes"])
        when_p, when_d, when_t = rng.choice(pack["when"])
        user = _fill(rng, rng.choice(pack["t_recipe"]), dish=dish, when=when_p)
        query = pack["q_recipe"].replace("{dish}", dish)
        # Round 1: ONLY the search — the note and reminder wait for results.
        r1 = {"user": user, "output": {"steps": [
            {"tool": "web_search", "args": {"query": query}}], "reply": rng.choice(pack["r_recipe_r1"])}}
        # Round 2: findings arrive; write the checklist note + buy reminder.
        findings = (f'Search results for "{query}":\n- {dish}: ' + ", ".join(ingredients) + ".\n- "
                    + " ".join(steps))
        note_title = pack["ing_title"].replace("{dish}", dish)
        r2_steps = [
            {"tool": "add_note", "args": {"title": note_title, "body": _checklist(ingredients)}},
            {"tool": "add_task", "args": {"title": pack["buy_title"].replace("{title}", note_title).replace("{title_lc}", note_title.lower()),
                                           "date": when_d, "time": when_t}},
        ]
        if rng.random() < 0.4:  # sometimes also file the method under the note
            r2_steps.append({"tool": "append_note", "args": {
                "title_contains": note_title, "text": pack["steps_head"] + "\n" + _numbered(steps)}})
        r2 = {"user": user, "findings": findings, "output": {
            "steps": r2_steps, "reply": _fill(rng, rng.choice(pack["r_recipe_r2"]), dish=dish, when=when_p)}}
        return [r1, r2]

    if kind == "append":
        items = _pick_items(rng, pack, 2)
        title = rng.choice(pack["list_titles"])
        user = _fill(rng, rng.choice(pack["t_append"]), title=title, items=_join(items, and_w))
        reply = _fill(rng, rng.choice(pack["r_append"]), title=title)
        return [{"user": user, "output": {"steps": [
            {"tool": "append_note", "args": {"title_contains": title, "text": _checklist(items)}}], "reply": reply}}]

    if kind == "rate":
        (a_name, a_code), (b_name, b_code) = rng.sample(pack["currencies"], 2)
        user = _fill(rng, rng.choice(pack["t_rate"]), a=a_name, b=b_name, a_s=a_name.rstrip("s"))
        reply = _fill(rng, rng.choice(pack["r_rate"]), A=a_code, B=b_code)
        return [{"user": user, "output": {"steps": [
            {"tool": "get_rate", "args": {"from": a_code, "to": b_code}}], "reply": reply}}]

    if kind == "convert":
        (a_name, a_code), (b_name, b_code) = rng.sample(pack["currencies"], 2)
        n = rng.choice(CONVERT_AMOUNTS)
        user = _fill(rng, rng.choice(pack["t_convert"]), n=str(n), a=a_name, b=b_name)
        reply = _fill(rng, rng.choice(pack["r_convert"]), n=str(n), A=a_code, B=b_code)
        return [{"user": user, "output": {"steps": [
            {"tool": "convert", "args": {"amount": n, "from": a_code, "to": b_code}}], "reply": reply}}]

    if kind == "rate_alert":
        (a_name, a_code), (b_name, b_code) = rng.sample(pack["currencies"], 2)
        n = rng.choice(RATE_THRESHOLDS)
        user = _fill(rng, rng.choice(pack["t_rate_alert"]), a=a_name, b=b_name, a_s=a_name.rstrip("s"), n=str(n))
        reply = _fill(rng, rng.choice(pack["r_rate_alert"]), n=str(n), A=a_code, B=b_code)
        return [{"user": user, "output": {"steps": [
            {"tool": "add_rate_alert", "args": {"from": a_code, "to": b_code, "threshold": n}}], "reply": reply}}]

    if kind == "trash":
        return [{"user": rng.choice(pack["t_trash"]), "output": {"steps": [
            {"tool": "check_trash", "args": {}}], "reply": rng.choice(pack["r_trash"])}}]

    if kind == "search_ss":
        thing, query, label, value = rng.choice(pack["ss_finds"])
        user = _fill(rng, rng.choice(pack["t_search_ss"]), thing=thing)
        # Round 1: ONLY the search — the answer waits for the matched text.
        r1 = {"user": user, "output": {"steps": [
            {"tool": "search_screenshots", "args": {"query": query}}], "reply": rng.choice(pack["r_search_ss_r1"])}}
        # Round 2: findings embedded (same shape the tool returns), answer only.
        findings = f'Screenshot text matching "{query}":\n- Screenshot_2027-05-09.png: …{value}…'
        reply = _fill(rng, rng.choice(pack["r_search_ss"]), label=label, value=value)
        r2 = {"user": user, "findings": findings, "output": {"steps": [], "reply": reply}}
        return [r1, r2]

    if kind == "expense":
        amt = rng.choice(AMOUNTS)
        phrase, cat, note = rng.choice(pack["spends"])
        user = _fill(rng, rng.choice(pack["t_expense"]), amt=str(amt), thing=phrase)
        reply = _fill(rng, rng.choice(pack["r_expense"]), amt=str(amt), cat=pack["cat_labels"].get(cat, cat))
        return [{"user": user, "output": {"steps": [
            {"tool": "add_expense", "args": {"amount": amt, "category": cat, "note": note}}], "reply": reply}}]

    if kind == "spend":
        period_p, period_d = rng.choice(pack["periods"])
        user = _fill(rng, rng.choice(pack["t_spend"]), period=period_p)
        # Round 1: ONLY the lookup — the answer needs the monthly digest.
        r1 = {"user": user, "output": {"steps": [
            {"tool": "list_expenses", "args": {"period": period_d}}], "reply": rng.choice(pack["r_spend_r1"])}}
        total = rng.choice([1520, 2340, 3210, 4520, 5890, 7150, 9990])
        cats = rng.sample(["food", "transport", "bills", "groceries", "shopping"], 3)
        split = [int(total * 0.5), int(total * 0.3), total - int(total * 0.5) - int(total * 0.3)]
        findings = (
            f"Expenses for {period_d}:\n"
            + f"Total: ₹{_group_indian(total)} across {rng.randint(8, 30)} entries\n"
            + "\n".join(f"- {c}: ₹{_group_indian(p)}" for c, p in zip(cats, split))
        )
        reply = _fill(rng, rng.choice(pack["r_spend"]), period=period_p,
                      total=f"₹{_group_indian(total)}", top=pack["cat_labels"].get(cats[0], cats[0]))
        r2 = {"user": user, "findings": findings, "output": {"steps": [], "reply": reply}}
        return [r1, r2]

    if kind == "agenda":
        day_p, day_d = rng.choice(pack["days"])
        user = _fill(rng, rng.choice(pack["t_agenda"]), day=day_p)
        # Round 1: ONLY the lookup — the answer waits for the agenda contents.
        r1 = {"user": user, "output": {"steps": [
            {"tool": "list_agenda", "args": {"date": day_d}}], "reply": rng.choice(pack["r_agenda_r1"])}}
        if rng.random() < 0.25:
            findings = f"Agenda for {day_d}:\nNo tasks or events on {day_d}."
            reply = _fill(rng, rng.choice(pack["r_agenda_empty"]), day=day_p)
        else:
            n = rng.choice([2, 3])
            lines, names = [], []
            for t in sorted(rng.sample(["09:00", "10:00", "12:30", "14:00", "17:00", "18:30"], n)):
                if rng.random() < 0.5:
                    ev_phrase, ev_title = rng.choice(pack["events"])
                    title = ev_title.replace("{person}", rng.choice(pack["people"]))
                    lines.append(f"- {t} {title} (event)")
                else:
                    _, title = rng.choice(pack["actions"])
                    lines.append(f"- {t} {title} (task, pending)")
                names.append(f"{title} ({t})")
            findings = f"Agenda for {day_d}:\n" + "\n".join(lines)
            reply = _fill(rng, rng.choice(pack["r_agenda"]), day=day_p, n=str(n), list=", ".join(names))
        # Round 2: findings embedded, answer only — no more tool calls.
        r2 = {"user": user, "findings": findings, "output": {"steps": [], "reply": reply}}
        return [r1, r2]

    if kind == "readnote":
        title = rng.choice(pack["list_titles"])
        items = _pick_items(rng, pack)
        act = rng.random() < 0.35  # "…and remind me to buy everything" variant
        if act:
            when_p, when_d, when_t = rng.choice(pack["when"])
            user = _fill(rng, rng.choice(pack["t_readnote_act"]), title=title, when=when_p)
        else:
            user = _fill(rng, rng.choice(pack["t_readnote"]), title=title)
        r1 = {"user": user, "output": {"steps": [
            {"tool": "read_note", "args": {"title_contains": title}}], "reply": rng.choice(pack["r_readnote_r1"])}}
        findings = f'Note "{title}":\n' + _checklist(items)
        if act:
            buy = pack["buy_title"].replace("{title}", title).replace("{title_lc}", title.lower())
            out = {"steps": [{"tool": "add_task", "args": {"title": buy, "date": when_d, "time": when_t}}],
                   "reply": _fill(rng, rng.choice(pack["r_readnote_act"]), title=title, when=when_p)}
        else:
            out = {"steps": [], "reply": _fill(rng, rng.choice(pack["r_readnote"]), title=title, list=", ".join(items))}
        r2 = {"user": user, "findings": findings, "output": out}
        return [r1, r2]

    if kind == "chat":
        user, reply = rng.choice(pack["chat"])
        return [{"user": user, "output": {"steps": [], "reply": reply}}]

    raise ValueError(kind)


def synthesize(rng, quotas: dict) -> list[dict]:
    """Compose ~quota seed-format rows per language; every row unique by user text
    (chat rows exempt — their bank is small and repetition is harmless)."""
    kinds = [k for k, w in WEIGHTS for _ in range(w)]
    rows = []
    for lang, quota in quotas.items():
        pack = PACKS[lang]
        seen: set = set()
        made, attempts = 0, 0
        while made < quota and attempts < quota * 60:
            attempts += 1
            batch = _build(rng, pack, rng.choice(kinds))
            key = batch[0]["user"]
            if key in seen and batch[0]["output"]["steps"]:
                continue
            seen.add(key)
            for row in batch:
                row["lang"] = lang
                rows.append(row)
                made += 1
    return rows
