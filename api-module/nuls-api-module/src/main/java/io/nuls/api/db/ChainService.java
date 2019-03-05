package io.nuls.api.db;


import com.mongodb.client.model.Filters;
import io.nuls.api.constant.MongoTableConstant;
import io.nuls.api.model.po.db.SyncInfo;
import io.nuls.api.utils.DocumentTransferTool;
import io.nuls.tools.core.annotation.Autowired;
import io.nuls.tools.core.annotation.Component;
import org.bson.Document;

@Component
public class ChainService {

    @Autowired
    private MongoDBService mongoDBService;

    public SyncInfo getSyncInfo(int chainId) {
        Document document = mongoDBService.findOne(MongoTableConstant.SYNC_INFO_TABLE, Filters.eq("_id", chainId));
        if (document == null) {
            return null;
        }
        return DocumentTransferTool.toInfo(document, "chainId", SyncInfo.class);
    }

}
