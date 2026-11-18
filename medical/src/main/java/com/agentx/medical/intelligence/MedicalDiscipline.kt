package com.agentx.medical.intelligence

/**
 * Recognized clinical disciplines for medical query classification and contextual prompting.
 */
enum class MedicalDiscipline(
    val id: String,
    val title: String,
    val description: String,
    val keywords: List<String>
) {
    GENERAL_MEDICINE(
        id = "GENERAL_MEDICINE",
        title = "Internal / General Medicine",
        description = "Systemic adult diseases, multi-morbid conditions, diagnostics, and therapeutics",
        keywords = listOf("internal medicine", "general medicine", "fever", "weight loss", "fatigue", "oedema", "hypertension", "diabetes", "sepsis")
    ),

    CARDIOLOGY(
        id = "CARDIOLOGY",
        title = "Cardiology & Vascular Medicine",
        description = "Cardiovascular disorders, hemodynamics, arrhythmias, ischemic heart disease, and heart failure",
        keywords = listOf("cardiac", "cardiology", "heart", "coronary", "angina", "myocardial", "stemi", "nstemi", "arrhythmia", "atrial fibrillation", "heart failure", "ecg", "troponin", "hypertension", "valvular", "murmur", "pericarditis", "endocarditis")
    ),

    PULMONOLOGY(
        id = "PULMONOLOGY",
        title = "Pulmonology & Respiratory Medicine",
        description = "Airway, parenchymal, vascular, and pleural lung disorders",
        keywords = listOf("pulmonology", "respiratory", "lung", "dyspnoea", "shortness of breath", "cough", "hemoptysis", "copd", "asthma", "pneumonia", "pulmonary embolism", "pleural effusion", "pneumothorax", "ards", "abg", "spirometry", "fev1")
    ),

    GASTROENTEROLOGY_HEPATOLOGY(
        id = "GASTROENTEROLOGY_HEPATOLOGY",
        title = "Gastroenterology & Hepatology",
        description = "Digestive tract, liver, biliary system, and pancreatic pathologies",
        keywords = listOf("gastroenterology", "hepatology", "gi", "liver", "cirrhosis", "jaundice", "hepatitis", "pancreatitis", "peptic ulcer", "gerd", "ibd", "crohn", "ulcerative colitis", "ascites", "variceal", "endoscopy", "lft", "bilirubin")
    ),

    NEPHROLOGY_UROLOGY(
        id = "NEPHROLOGY_UROLOGY",
        title = "Nephrology & Urology",
        description = "Renal parenchymal disease, electrolytes, acute/chronic kidney failure, and urinary tract disorders",
        keywords = listOf("nephrology", "urology", "kidney", "renal", "creatinine", "egfr", "aki", "ckd", "glomerulonephritis", "nephrotic", "nephritic", "dialysis", "proteinuria", "hematuria", "hyperkalemia", "electrolyte", "acid-base", "uti", "nephrolithiasis", "kidney stone")
    ),

    NEUROLOGY(
        id = "NEUROLOGY",
        title = "Neurology",
        description = "Central and peripheral nervous system disorders, stroke, neurodegeneration, and neuromuscular illness",
        keywords = listOf("neurology", "brain", "stroke", "tia", "seizure", "epilepsy", "headache", "migraine", "neuropathy", "parkinson", "dementia", "multiple sclerosis", "meningitis", "encephalitis", "cranial nerve", "gcs", "nihss", "lp", "csf")
    ),

    ENDOCRINOLOGY_METABOLISM(
        id = "ENDOCRINOLOGY_METABOLISM",
        title = "Endocrinology & Metabolism",
        description = "Hormonal dysregulation, diabetes, thyroid, adrenal, pituitary, and bone metabolism",
        keywords = listOf("endocrinology", "endocrine", "diabetes", "dka", "hhs", "insulin", "thyroid", "hypothyroidism", "hyperthyroidism", "tsh", "adrenal", "cushing", "addison", "cortisol", "pituitary", "calcium", "hypercalcemia", "osteoporosis")
    ),

    HAEMATOLOGY_ONCOLOGY(
        id = "HAEMATOLOGY_ONCOLOGY",
        title = "Haematology & Oncology",
        description = "Blood cell disorders, coagulopathies, hematological malignancies, and solid tumors",
        keywords = listOf("haematology", "hematology", "oncology", "cancer", "anemia", "thrombocytopenia", "leukemia", "lymphoma", "myeloma", "coagulation", "inr", "pt", "aptt", "d-dimer", "chemotherapy", "neutropenia", "transfusion", "sickle cell", "thalassemia")
    ),

    INFECTIOUS_DISEASES(
        id = "INFECTIOUS_DISEASES",
        title = "Infectious Diseases & Microbiology",
        description = "Bacterial, viral, fungal, parasitic infections, antimicrobial stewardship, and travel medicine",
        keywords = listOf("infectious", "infection", "bacterial", "viral", "fungal", "parasite", "antibiotic", "antiviral", "antimicrobial", "hiv", "tuberculosis", "tb", "malaria", "dengue", "sepsis", "bacteremia", "endocarditis", "covid", "meningitis")
    ),

    RHEUMATOLOGY_IMMUNOLOGY(
        id = "RHEUMATOLOGY_IMMUNOLOGY",
        title = "Rheumatology & Clinical Immunology",
        description = "Autoimmune, autoinflammatory, musculoskeletal, and connective tissue diseases",
        keywords = listOf("rheumatology", "immunology", "arthritis", "rheumatoid", "sle", "lupus", "vasculitis", "gout", "spondylitis", "ana", "anti-ccp", "crp", "esr", "autoimmune", "scleroderma", "myositis")
    ),

    EMERGENCY_CRITICAL_CARE(
        id = "EMERGENCY_CRITICAL_CARE",
        title = "Emergency Medicine & Intensive Care",
        description = "Acute resuscitation, airway management, shock states, multi-organ failure, and trauma",
        keywords = listOf("emergency", "critical care", "icu", "resuscitation", "abcde", "shock", "cardiac arrest", "anaphylaxis", "trauma", "overdose", "poisoning", "intubation", "mechanical ventilation", "vasopressor", "noradrenaline", "status epilepticus")
    ),

    GENERAL_SURGERY(
        id = "GENERAL_SURGERY",
        title = "General Surgery & Surgical Specialties",
        description = "Acute abdomen, perioperative optimization, wound care, and surgical emergencies",
        keywords = listOf("surgery", "surgical", "acute abdomen", "appendicitis", "cholecystitis", "bowel obstruction", "perforation", "hernia", "preoperative", "postoperative", "laparotomy", "laparoscopic", "wound", "dehiscence")
    ),

    OBSTETRICS_GYNAECOLOGY(
        id = "OBSTETRICS_GYNAECOLOGY",
        title = "Obstetrics & Gynaecology",
        description = "Maternal-fetal medicine, labour and delivery, obstetric emergencies, and reproductive health",
        keywords = listOf("obstetrics", "gynaecology", "gynecology", "pregnancy", "pregnant", "labour", "delivery", "postpartum", "pph", "preeclampsia", "eclampsia", "ectopic", "cesarean", "menorrhagia", "cervical", "ovarian", "fetal")
    ),

    PAEDIATRICS_NEONATOLOGY(
        id = "PAEDIATRICS_NEONATOLOGY",
        title = "Paediatrics & Neonatology",
        description = "Infant, child, and adolescent medicine, congenital conditions, growth, development, and pediatric dosing",
        keywords = listOf("paediatrics", "pediatrics", "child", "infant", "neonatal", "neonate", "newborn", "growth", "vaccination", "croup", "bronchiolitis", "pediatric dosing", "apgar", "kawasaki", "febrile seizure", "congenital")
    ),

    PSYCHIATRY_BEHAVIOURAL(
        id = "PSYCHIATRY_BEHAVIOURAL",
        title = "Psychiatry & Behavioural Sciences",
        description = "Mental health, psychiatric crises, psychopharmacology, neuroses, and psychoses",
        keywords = listOf("psychiatry", "psychiatric", "depression", "anxiety", "psychosis", "schizophrenia", "bipolar", "suicide", "suicidal", "delirium", "dementia", "ssri", "antipsychotic", "mood", "substance abuse", "addiction")
    ),

    DERMATOLOGY(
        id = "DERMATOLOGY",
        title = "Dermatology",
        description = "Cutaneous disorders, rashes, systemic manifestations, and dermatopathology",
        keywords = listOf("dermatology", "skin", "rash", "erythema", "lesion", "psoriasis", "eczema", "dermatitis", "melanoma", "urticaria", "steven johnson", "ten", "pemphigus", "pruritus", "ulcer")
    ),

    OPHTHALMOLOGY(
        id = "OPHTHALMOLOGY",
        title = "Ophthalmology",
        description = "Eye disorders, visual pathways, retinal pathology, and acute ocular emergencies",
        keywords = listOf("ophthalmology", "eye", "vision", "cornea", "retina", "glaucoma", "uveitis", "cataract", "red eye", "papilloedema", "retinal detachment", "visual loss")
    ),

    OTORHINOLARYNGOLOGY_ENT(
        id = "OTORHINOLARYNGOLOGY_ENT",
        title = "Otorhinolaryngology (ENT)",
        description = "Ear, nose, throat, larynx, head, and neck pathologies",
        keywords = listOf("ent", "otorhinolaryngology", "ear", "nose", "throat", "larynx", "tonsillitis", "epistaxis", "vertigo", "meniere", "stridor", "otitis", "sinusitis", "vocal cord")
    ),

    ANAESTHESIOLOGY_PAIN(
        id = "ANAESTHESIOLOGY_PAIN",
        title = "Anaesthesiology & Pain Medicine",
        description = "Perioperative analgesia, regional anaesthesia, sedation, hemodynamic monitoring, and acute/chronic pain",
        keywords = listOf("anaesthesia", "anesthesia", "analgesia", "pain", "sedation", "airway", "mac", "spinal", "epidural", "propofol", "fentanyl", "neuromuscular blockade", "postop pain")
    ),

    GENETICS_GENOMICS(
        id = "GENETICS_GENOMICS",
        title = "Medical Genetics & Genomics",
        description = "Hereditary conditions, chromosomal abnormalities, metabolic errors, and genetic counselling",
        keywords = listOf("genetics", "genomics", "inheritance", "mutation", "chromosomal", "inborn errors", "metabolic", "pedigree", "karyotype", "monogenic")
    ),

    PREVENTIVE_COMMUNITY_HEALTH(
        id = "PREVENTIVE_COMMUNITY_HEALTH",
        title = "Preventive & Community Medicine",
        description = "Public health, epidemiology, immunization schedules, screening programs, and occupational health",
        keywords = listOf("preventive", "community medicine", "public health", "epidemiology", "screening", "vaccine", "immunization", "prevention", "outbreak", "occupational")
    );

    companion object {
        fun match(query: String): MedicalDiscipline {
            val lower = query.lowercase()
            for (discipline in entries) {
                if (discipline.keywords.any { lower.contains(it) }) {
                    return discipline
                }
            }
            return GENERAL_MEDICINE
        }
    }
}
