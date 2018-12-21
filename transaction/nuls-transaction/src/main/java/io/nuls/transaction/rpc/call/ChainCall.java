package io.nuls.transaction.rpc.call;

import io.nuls.base.data.Transaction;
import io.nuls.rpc.client.CmdDispatcher;
import io.nuls.rpc.info.Constants;
import io.nuls.rpc.model.ModuleE;
import io.nuls.rpc.model.message.Response;
import io.nuls.tools.exception.NulsException;
import io.nuls.transaction.model.bo.Chain;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * 调用其他模块跟交易相关的接口
 *
 * @author: qinyifeng
 * @date: 2018/12/20
 */
public class ChainCall {

    /**
     * 查资产是否存在
     * @param chainId
     * @param assetId
     * @return
     */
    public static boolean verifyAssetExist(int chainId, int assetId) {
        HashMap params = new HashMap();
        params.put("chianId", chainId);
        params.put("assetId", assetId);
        HashMap result = (HashMap) TransactionCall.request("cm_asset", ModuleE.CM.abbr, params);
        if (result.get("assetId") != null) {
            return true;
        }
        return false;
    }

}
