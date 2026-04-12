package com.seggellion.britannia_mod.quest;

import com.seggellion.britannia_mod.quest.network.QuestModels.QuestResponse;

public class QuestManager {
    private static final QuestManager INSTANCE = new QuestManager();
    private QuestResponse currentQuestState;

    private QuestManager() {}

    public static QuestManager getInstance() {
        return INSTANCE;
    }

    public void setCurrentQuestState(QuestResponse state) {
        this.currentQuestState = state;
    }

    public QuestResponse getCurrentQuestState() {
        return this.currentQuestState;
    }

    public boolean hasActiveQuest() {
        return currentQuestState != null && !currentQuestState.completed;
    }
    
    public void clearState() {
        this.currentQuestState = null;
    }
}