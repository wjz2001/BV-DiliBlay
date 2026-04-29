package dev.aaa1115910.bv.util

import android.content.Context
import dev.aaa1115910.biliapi.entity.ugc.UgcType
import dev.aaa1115910.bv.R

fun UgcType.getDisplayName(context: Context) = when (this) {
    UgcType.Douga -> R.string.ugc_type_v2_douga
    UgcType.DougaFanAnime -> R.string.ugc_type_v2_douga_fan_anime
    UgcType.DougaGarageKit -> R.string.ugc_type_v2_douga_garage_kit
    UgcType.DougaCosplay -> R.string.ugc_type_v2_douga_osplay
    UgcType.DougaOffline -> R.string.ugc_type_v2_douga_offline
    UgcType.DougaEditing -> R.string.ugc_type_v2_douga_editing
    UgcType.DougaCommentary -> R.string.ugc_type_v2_douga_commentary
    UgcType.DougaQuickView -> R.string.ugc_type_v2_douga_quick_view
    UgcType.DougaVoice -> R.string.ugc_type_v2_douga_voice
    UgcType.DougaInformation -> R.string.ugc_type_v2_douga_information
    UgcType.DougaInterpret -> R.string.ugc_type_v2_douga_interpret
    UgcType.DougaVup -> R.string.ugc_type_v2_douga_vup
    UgcType.DougaTokusatsu -> R.string.ugc_type_v2_douga_tokusatsu
    UgcType.DougaPuppetry -> R.string.ugc_type_v2_douga_puppetry
    UgcType.DougaComic -> R.string.ugc_type_v2_douga_comic
    UgcType.DougaMotion -> R.string.ugc_type_v2_douga_motion
    UgcType.DougaReaction -> R.string.ugc_type_v2_douga_reaction
    UgcType.DougaTutorial -> R.string.ugc_type_v2_douga_tutorial
    UgcType.DougaOther -> R.string.ugc_type_v2_douga_other

    UgcType.Game -> R.string.ugc_type_v2_game
    UgcType.GameRpg -> R.string.ugc_type_v2_game_rpg
    UgcType.GameMmorpg -> R.string.ugc_type_v2_game_mmorpg
    UgcType.GameStandAlone -> R.string.ugc_type_v2_game_stand_alone
    UgcType.GameSlg -> R.string.ugc_type_v2_game_slg
    UgcType.GameTbs -> R.string.ugc_type_v2_game_tbs
    UgcType.GameRts -> R.string.ugc_type_v2_game_rts
    UgcType.GameMoba -> R.string.ugc_type_v2_game_moba
    UgcType.GameStg -> R.string.ugc_type_v2_game_stg
    UgcType.GameSpg -> R.string.ugc_type_v2_game_spg
    UgcType.GameAct -> R.string.ugc_type_v2_game_act
    UgcType.GameMsc -> R.string.ugc_type_v2_game_msc
    UgcType.GameSim -> R.string.ugc_type_v2_game_sim
    UgcType.GameOtome -> R.string.ugc_type_v2_game_otome
    UgcType.GamePuz -> R.string.ugc_type_v2_game_puz
    UgcType.GameSandbox -> R.string.ugc_type_v2_game_sandbox
    UgcType.GameOther -> R.string.ugc_type_v2_game_other

    UgcType.Kichiku -> R.string.ugc_type_v2_kichiku
    UgcType.KichikuGuide -> R.string.ugc_type_v2_kichiku_guide
    UgcType.KichikuTheatre -> R.string.ugc_type_v2_kichiku_theatre
    UgcType.KichikuManualVocaloid -> R.string.ugc_type_v2_kichiku_manual_vocaloid
    UgcType.KichikuMad -> R.string.ugc_type_v2_kichiku_mad
    UgcType.KichikuOther -> R.string.ugc_type_v2_kichiku_other

    UgcType.Music -> R.string.ugc_type_v2_music
    UgcType.MusicOriginal -> R.string.ugc_type_v2_music_original
    UgcType.MusicMv -> R.string.ugc_type_v2_music_mv
    UgcType.MusicLive -> R.string.ugc_type_v2_music_live
    UgcType.MusicFanVideos -> R.string.ugc_type_v2_music_fan_videos
    UgcType.MusicCover -> R.string.ugc_type_v2_music_cover
    UgcType.MusicPerform -> R.string.ugc_type_v2_music_perform
    UgcType.MusicVocaloid -> R.string.ugc_type_v2_music_vocaloid
    UgcType.MusicAiMusic -> R.string.ugc_type_v2_music_ai_music
    UgcType.MusicRadio -> R.string.ugc_type_v2_music_radio
    UgcType.MusicTutorial -> R.string.ugc_type_v2_music_tutorial
    UgcType.MusicCommentary -> R.string.ugc_type_v2_music_commentary
    UgcType.MusicOther -> R.string.ugc_type_v2_music_other

    UgcType.Dance -> R.string.ugc_type_v2_dance
    UgcType.DanceOtaku -> R.string.ugc_type_v2_dance_otaku
    UgcType.DanceHiphop -> R.string.ugc_type_v2_dance_hiphop
    UgcType.DanceGestures -> R.string.ugc_type_v2_dance_gestures
    UgcType.DanceStar -> R.string.ugc_type_v2_dance_star
    UgcType.DanceChina -> R.string.ugc_type_v2_dance_china
    UgcType.DanceTutorial -> R.string.ugc_type_v2_dance_tutorial
    UgcType.DanceBallet -> R.string.ugc_type_v2_dance_ballet
    UgcType.DanceWota -> R.string.ugc_type_v2_dance_wota
    UgcType.DanceOther -> R.string.ugc_type_v2_dance_other

    UgcType.Cinephile -> R.string.ugc_type_v2_cinephile
    UgcType.CinephileCommentary -> R.string.ugc_type_v2_cinephile_commentary
    UgcType.CinephileMontage -> R.string.ugc_type_v2_cinephile_montage
    UgcType.CinephileInformation -> R.string.ugc_type_v2_cinephile_information
    UgcType.CinephilePorterage -> R.string.ugc_type_v2_cinephile_porterage
    UgcType.CinephileShortFilm -> R.string.ugc_type_v2_cinephile_shortfilm
    UgcType.CinephileAi -> R.string.ugc_type_v2_cinephile_ai
    UgcType.CinephileReaction -> R.string.ugc_type_v2_cinephile_reaction
    UgcType.CinephileOther -> R.string.ugc_type_v2_cinephile_other

    UgcType.Ent -> R.string.ugc_type_v2_ent
    UgcType.EntCommentary -> R.string.ugc_type_v2_ent_commentary
    UgcType.EntMontage -> R.string.ugc_type_v2_ent_montage
    UgcType.EntFansVideo -> R.string.ugc_type_v2_ent_fans_video
    UgcType.EntInformation -> R.string.ugc_type_v2_ent_information
    UgcType.EntReaction -> R.string.ugc_type_v2_ent_reaction
    UgcType.EntVariety -> R.string.ugc_type_v2_ent_variety
    UgcType.EntOther -> R.string.ugc_type_v2_ent_other

    UgcType.Knowledge -> R.string.ugc_type_v2_knowledge
    UgcType.KnowledgeExam -> R.string.ugc_type_v2_knowledge_exam
    UgcType.KnowledgeLangSkill -> R.string.ugc_type_v2_knowledge_lang_skill
    UgcType.KnowledgeCampus -> R.string.ugc_type_v2_knowledge_campus
    UgcType.KnowledgeBusiness -> R.string.ugc_type_v2_knowledge_business
    UgcType.KnowledgeSocialObservation -> R.string.ugc_type_v2_knowledge_social_observation
    UgcType.KnowledgePolitics -> R.string.ugc_type_v2_knowledge_politics
    UgcType.KnowledgeHumanityHistory -> R.string.ugc_type_v2_knowledge_humanity_history
    UgcType.KnowledgeDesign -> R.string.ugc_type_v2_knowledge_design
    UgcType.KnowledgePsychology -> R.string.ugc_type_v2_knowledge_psychology
    UgcType.KnowledgeCareer -> R.string.ugc_type_v2_knowledge_career
    UgcType.KnowledgeScience -> R.string.ugc_type_v2_knowledge_science
    UgcType.KnowledgeOther -> R.string.ugc_type_v2_knowledge_other

    UgcType.Tech -> R.string.ugc_type_v2_tech
    UgcType.TechComputer -> R.string.ugc_type_v2_tech_computer
    UgcType.TechPhone -> R.string.ugc_type_v2_tech_phone
    UgcType.TechPad -> R.string.ugc_type_v2_tech_pad
    UgcType.TechPhotography -> R.string.ugc_type_v2_tech_photography
    UgcType.TechMachine -> R.string.ugc_type_v2_tech_machine
    UgcType.TechCreate -> R.string.ugc_type_v2_tech_create
    UgcType.TechOther -> R.string.ugc_type_v2_tech_other

    UgcType.Information -> R.string.ugc_type_v2_information
    UgcType.InformationPolitics -> R.string.ugc_type_v2_information_politics
    UgcType.InformationOverseas -> R.string.ugc_type_v2_information_overseas
    UgcType.InformationSocial -> R.string.ugc_type_v2_information_social
    UgcType.InformationOther -> R.string.ugc_type_v2_information_other

    UgcType.Food -> R.string.ugc_type_v2_food
    UgcType.FoodMake -> R.string.ugc_type_v2_food_make
    UgcType.FoodDetective -> R.string.ugc_type_v2_food_detective
    UgcType.FoodCommentary -> R.string.ugc_type_v2_food_commentary
    UgcType.FoodRecord -> R.string.ugc_type_v2_food_record
    UgcType.FoodOther -> R.string.ugc_type_v2_food_other

    UgcType.Shortplay -> R.string.ugc_type_v2_shortplay
    UgcType.ShortplayPlot -> R.string.ugc_type_v2_shortplay_plot
    UgcType.ShortplayLang -> R.string.ugc_type_v2_shortplay_lang
    UgcType.ShortplayUpVariety -> R.string.ugc_type_v2_shortplay_up_variety
    UgcType.ShortplayInterview -> R.string.ugc_type_v2_shortplay_interview

    UgcType.Car -> R.string.ugc_type_v2_car
    UgcType.CarCommentary -> R.string.ugc_type_v2_car_commentary
    UgcType.CarCulture -> R.string.ugc_type_v2_car_culture
    UgcType.CarLife -> R.string.ugc_type_v2_car_life
    UgcType.CarTech -> R.string.ugc_type_v2_car_tech
    UgcType.CarOther -> R.string.ugc_type_v2_car_other

    UgcType.Fashion -> R.string.ugc_type_v2_fashion
    UgcType.FashionMakeup -> R.string.ugc_type_v2_fashion_makeup
    UgcType.FashionSkincare -> R.string.ugc_type_v2_fashion_skincare
    UgcType.FashionCos -> R.string.ugc_type_v2_fashion_cos
    UgcType.FashionOutfits -> R.string.ugc_type_v2_fashion_outfits
    UgcType.FashionAccessories -> R.string.ugc_type_v2_fashion_accessories
    UgcType.FashionJewelry -> R.string.ugc_type_v2_fashion_jewelry
    UgcType.FashionTrick -> R.string.ugc_type_v2_fashion_trick
    UgcType.FashionCommentary -> R.string.ugc_type_v2_fashion_commentary
    UgcType.FashionOther -> R.string.ugc_type_v2_fashion_other

    UgcType.Sports -> R.string.ugc_type_v2_sports
    UgcType.SportsTrend -> R.string.ugc_type_v2_sports_trend
    UgcType.SportsFootball -> R.string.ugc_type_v2_sports_football
    UgcType.SportsBasketball -> R.string.ugc_type_v2_sports_basketball
    UgcType.SportsRunning -> R.string.ugc_type_v2_sports_running
    UgcType.SportsKungfu -> R.string.ugc_type_v2_sports_kungfu
    UgcType.SportsFighting -> R.string.ugc_type_v2_sports_fighting
    UgcType.SportsBadminton -> R.string.ugc_type_v2_sports_badminton
    UgcType.SportsInformation -> R.string.ugc_type_v2_sports_information
    UgcType.SportsMatch -> R.string.ugc_type_v2_sports_match
    UgcType.SportsOther -> R.string.ugc_type_v2_sports_other

    UgcType.Animal -> R.string.ugc_type_v2_animal
    UgcType.AnimalCat -> R.string.ugc_type_v2_animal_cat
    UgcType.AnimalDog -> R.string.ugc_type_v2_animal_dog
    UgcType.AnimalReptiles -> R.string.ugc_type_v2_animal_reptiles
    UgcType.AnimalScience -> R.string.ugc_type_v2_animal_science
    UgcType.AnimalOther -> R.string.ugc_type_v2_animal_other

    UgcType.Vlog -> R.string.ugc_type_v2_vlog
    UgcType.VlogLife -> R.string.ugc_type_v2_vlog_life
    UgcType.VlogStudent -> R.string.ugc_type_v2_vlog_student
    UgcType.VlogCareer -> R.string.ugc_type_v2_vlog_career
    UgcType.VlogOther -> R.string.ugc_type_v2_vlog_other

    UgcType.Painting -> R.string.ugc_type_v2_painting
    UgcType.PaintingAcg -> R.string.ugc_type_v2_painting_acg
    UgcType.PaintingNoneAcg -> R.string.ugc_type_v2_painting_none_acg
    UgcType.PaintingTutorial -> R.string.ugc_type_v2_painting_tutorial
    UgcType.PaintingOther -> R.string.ugc_type_v2_painting_other

    UgcType.Ai -> R.string.ugc_type_v2_ai
    UgcType.AiTutorial -> R.string.ugc_type_v2_ai_tutorial
    UgcType.AiInformation -> R.string.ugc_type_v2_ai_information
    UgcType.AiOther -> R.string.ugc_type_v2_ai_other

    UgcType.Home -> R.string.ugc_type_v2_home
    UgcType.HomeTrade -> R.string.ugc_type_v2_home_trade
    UgcType.HomeRenovation -> R.string.ugc_type_v2_home_renovation
    UgcType.HomeFurniture -> R.string.ugc_type_v2_home_furniture
    UgcType.HomeAppliances -> R.string.ugc_type_v2_home_appliances

    UgcType.Outdoors -> R.string.ugc_type_v2_outdoors
    UgcType.OutdoorsCamping -> R.string.ugc_type_v2_outdoors_camping
    UgcType.OutdoorsHiking -> R.string.ugc_type_v2_outdoors_hiking
    UgcType.OutdoorsExplore -> R.string.ugc_type_v2_outdoors_explore
    UgcType.OutdoorsOther -> R.string.ugc_type_v2_outdoors_other

    UgcType.Gym -> R.string.ugc_type_v2_gym
    UgcType.GymScience -> R.string.ugc_type_v2_gym_science
    UgcType.GymTutorial -> R.string.ugc_type_v2_gym_tutorial
    UgcType.GymRecord -> R.string.ugc_type_v2_gym_record
    UgcType.GymFigure -> R.string.ugc_type_v2_gym_figure
    UgcType.GymOther -> R.string.ugc_type_v2_gym_other

    UgcType.Handmake -> R.string.ugc_type_v2_handmake
    UgcType.HandmakeHandbook -> R.string.ugc_type_v2_handmake_handbook
    UgcType.HandmakeLight -> R.string.ugc_type_v2_handmake_light
    UgcType.HandmakeTraditional -> R.string.ugc_type_v2_handmake_traditional
    UgcType.HandmakeRelief -> R.string.ugc_type_v2_handmake_relief
    UgcType.HandmakeDiy -> R.string.ugc_type_v2_handmake_diy
    UgcType.HandmakeOther -> R.string.ugc_type_v2_handmake_other

    UgcType.Travel -> R.string.ugc_type_v2_travel
    UgcType.TravelRecord -> R.string.ugc_type_v2_travel_record
    UgcType.TravelStrategy -> R.string.ugc_type_v2_travel_strategy
    UgcType.TravelCity -> R.string.ugc_type_v2_travel_city
    UgcType.TravelTransport -> R.string.ugc_type_v2_travel_transport

    UgcType.Rural -> R.string.ugc_type_v2_rural
    UgcType.RuralPlanting -> R.string.ugc_type_v2_rural_planting
    UgcType.RuralFishing -> R.string.ugc_type_v2_rural_fishing
    UgcType.RuralHarvest -> R.string.ugc_type_v2_rural_harvest
    UgcType.RuralTech -> R.string.ugc_type_v2_rural_tech
    UgcType.RuralLife -> R.string.ugc_type_v2_rural_life

    UgcType.Parenting -> R.string.ugc_type_v2_parenting
    UgcType.ParentingPregnantCare -> R.string.ugc_type_v2_parenting_pregnant_care
    UgcType.ParentingInfantCare -> R.string.ugc_type_v2_parenting_infant_care
    UgcType.ParentingTalent -> R.string.ugc_type_v2_parenting_talent
    UgcType.ParentingCute -> R.string.ugc_type_v2_parenting_cute
    UgcType.ParentingInteraction -> R.string.ugc_type_v2_parenting_interaction
    UgcType.ParentingEducation -> R.string.ugc_type_v2_parenting_education
    UgcType.ParentingOther -> R.string.ugc_type_v2_parenting_other

    UgcType.Health -> R.string.ugc_type_v2_health
    UgcType.HealthScience -> R.string.ugc_type_v2_health_science
    UgcType.HealthRegimen -> R.string.ugc_type_v2_health_regimen
    UgcType.HealthSexes -> R.string.ugc_type_v2_health_sexes
    UgcType.HealthPsychology -> R.string.ugc_type_v2_health_psychology
    UgcType.HealthAsmr -> R.string.ugc_type_v2_health_asmr
    UgcType.HealthOther -> R.string.ugc_type_v2_health_other

    UgcType.Emotion -> R.string.ugc_type_v2_emotion
    UgcType.EmotionFamily -> R.string.ugc_type_v2_emotion_family
    UgcType.EmotionRomantic -> R.string.ugc_type_v2_emotion_romantic
    UgcType.EmotionInterpersonal -> R.string.ugc_type_v2_emotion_interpersonal
    UgcType.EmotionGrowth -> R.string.ugc_type_v2_emotion_growth

    UgcType.LifeJoy -> R.string.ugc_type_v2_life_joy
    UgcType.LifeJoyLeisure -> R.string.ugc_type_v2_life_joy_leisure
    UgcType.LifeJoyOnSite -> R.string.ugc_type_v2_life_joy_on_site
    UgcType.LifeJoyArtisticProducts -> R.string.ugc_type_v2_life_joy_artistic_products
    UgcType.LifeJoyTrendyToys -> R.string.ugc_type_v2_life_joy_trendy_toys
    UgcType.LifeJoyOther -> R.string.ugc_type_v2_life_joy_other

    UgcType.LifeExperience -> R.string.ugc_type_v2_life_experience
    UgcType.LifeExperienceSkills -> R.string.ugc_type_v2_life_experience_skills
    UgcType.LifeExperienceProcedures -> R.string.ugc_type_v2_life_experience_procedures
    UgcType.LifeExperienceMarriage -> R.string.ugc_type_v2_life_experience_marriage

    UgcType.Mysticism -> R.string.ugc_type_v2_mysticism
    UgcType.MysticismTarot -> R.string.ugc_type_v2_mysticism_tarot
    UgcType.MysticismHoroscope -> R.string.ugc_type_v2_mysticism_horoscope
    UgcType.MysticismMetaphysics -> R.string.ugc_type_v2_mysticism_metaphysics
    UgcType.MysticismHealing -> R.string.ugc_type_v2_mysticism_healing
    UgcType.MysticismOther -> R.string.ugc_type_v2_mysticism_other
}.stringRes(context)