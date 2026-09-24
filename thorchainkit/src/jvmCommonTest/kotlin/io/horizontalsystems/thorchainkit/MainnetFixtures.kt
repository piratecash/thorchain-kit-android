package io.horizontalsystems.thorchainkit

// Real mainnet responses, trimmed to the fields the kit reads.
internal object MainnetFixtures {

    // real mainnet Midgard send action; its networkFees (20000000) is not what the sender paid (THOR_SEND)
    const val THOR_SEND_ACTION = """
        {
          "date": "1784454197789344321",
          "height": "27069723",
          "in": [
            {
              "address": "thor1t60f02r8jvzjrhtnjgfj4ne6rs5wjnejwmj7fh",
              "coins": [{"amount": "950009120000", "asset": "THOR.RUNE"}],
              "txID": "E0C97FCAB81C8CF22B235F38A7CAA97134719BD36C26746DA900B1DC7424E460"
            }
          ],
          "metadata": {
            "send": {
              "code": "0",
              "memo": "hello",
              "networkFees": [{"amount": "20000000", "asset": "THOR.RUNE"}],
              "reason": ""
            }
          },
          "out": [
            {
              "address": "thor166n4w5039meulfa3p6ydg60ve6ueac7tlt0jws",
              "coins": [{"amount": "950009120000", "asset": "THOR.RUNE"}],
              "txID": "E0C97FCAB81C8CF22B235F38A7CAA97134719BD36C26746DA900B1DC7424E460"
            }
          ],
          "pools": [],
          "status": "success",
          "type": "send"
        }
    """

    // real mainnet Midgard action: a BOND that was included in a block but failed
    // with insufficient funds — Midgard still reports status "success"
    const val THOR_FAILED_ACTION = """
        {
          "date": "1790070519718577133",
          "height": "27937636",
          "in": [
            {
              "address": "thor1z3e8pxs5fpgcdjpnn92y7xfv90enqm46qtxdl2",
              "coins": [{"amount": "641972000000", "asset": "THOR.RUNE"}],
              "txID": "A9AFEA02641C049E08CC310E626290EC7B22B83EB796AA36F0B202181C321C8E"
            }
          ],
          "metadata": {
            "failed": {
              "code": "5",
              "memo": "BOND:thor1fj6zv7uvn0t898ch7sxlmjept7lfdnrer8rtpq",
              "reason": "failed to execute message; message index: 0: insufficient funds"
            }
          },
          "out": [],
          "pools": [],
          "status": "success",
          "type": "failed"
        }
    """


    // real THORChain MsgSend E0C97FCA; Midgard reports networkFees 20000000 for it
    const val THOR_SEND = """
        {"tx_response":{"height":"27069723","txhash":"E0C97FCAB81C8CF22B235F38A7CAA97134719BD36C26746DA900B1DC7424E460","code":0,"tx":{"body":{"messages":[{"@type":"/types.MsgSend","from_address":"thor1t60f02r8jvzjrhtnjgfj4ne6rs5wjnejwmj7fh","to_address":"thor166n4w5039meulfa3p6ydg60ve6ueac7tlt0jws","amount":[{"denom":"rune","amount":"950009120000"}]}]}},"events":[
            {"type":"coin_spent","attributes":[{"key":"spender","value":"thor1t60f02r8jvzjrhtnjgfj4ne6rs5wjnejwmj7fh"},{"key":"amount","value":"2000000rune"}]},
            {"type":"coin_received","attributes":[{"key":"receiver","value":"thor1dheycdevq39qlkxs2a6wuuzyn4aqxhve4qxtxt"},{"key":"amount","value":"2000000rune"}]},
            {"type":"transfer","attributes":[{"key":"recipient","value":"thor1dheycdevq39qlkxs2a6wuuzyn4aqxhve4qxtxt"},{"key":"sender","value":"thor1t60f02r8jvzjrhtnjgfj4ne6rs5wjnejwmj7fh"},{"key":"amount","value":"2000000rune"}]},
            {"type":"message","attributes":[{"key":"sender","value":"thor1t60f02r8jvzjrhtnjgfj4ne6rs5wjnejwmj7fh"}]},
            {"type":"tx","attributes":[{"key":"acc_seq","value":"thor1t60f02r8jvzjrhtnjgfj4ne6rs5wjnejwmj7fh/365994"}]},
            {"type":"tx","attributes":[{"key":"signature","value":"x9iGyL0NYXYLRiW3B69hD06Vn8gnX06TmMVxwoNexc94xhmcVIu/so5U52JXaQtAiYFpDA2UNYXqo/x7L4uWnQ=="}]},
            {"type":"message","attributes":[{"key":"action","value":"/types.MsgSend"},{"key":"sender","value":"thor1t60f02r8jvzjrhtnjgfj4ne6rs5wjnejwmj7fh"},{"key":"module","value":"MsgSend"},{"key":"msg_index","value":"0"}]},
            {"type":"coin_spent","attributes":[{"key":"spender","value":"thor1t60f02r8jvzjrhtnjgfj4ne6rs5wjnejwmj7fh"},{"key":"amount","value":"950009120000rune"},{"key":"msg_index","value":"0"}]},
            {"type":"coin_received","attributes":[{"key":"receiver","value":"thor166n4w5039meulfa3p6ydg60ve6ueac7tlt0jws"},{"key":"amount","value":"950009120000rune"},{"key":"msg_index","value":"0"}]},
            {"type":"transfer","attributes":[{"key":"recipient","value":"thor166n4w5039meulfa3p6ydg60ve6ueac7tlt0jws"},{"key":"sender","value":"thor1t60f02r8jvzjrhtnjgfj4ne6rs5wjnejwmj7fh"},{"key":"amount","value":"950009120000rune"},{"key":"msg_index","value":"0"}]},
            {"type":"message","attributes":[{"key":"sender","value":"thor1t60f02r8jvzjrhtnjgfj4ne6rs5wjnejwmj7fh"},{"key":"msg_index","value":"0"}]}
        ]}}
    """

    // real THORChain MsgDeposit swap RUNE -> BCH~BCH
    const val THOR_SWAP_DEPOSIT = """
        {"tx_response":{"height":"27961524","txhash":"59444566178C0DFDE426F95E2E5AFDF9BB05D5CF5662BA1D70ED1D2428ECC3D1","code":0,"tx":{"body":{"messages":[{"@type":"/types.MsgDeposit","coins":[{"asset":"THOR.RUNE","amount":"193945327992","decimals":"0"}],"memo":"=:BCH~BCH:thor17hwqt302e5f2xm4h95ma8wuggqkvfzgvsnh5z9:352271308/1/1","signer":"thor17hwqt302e5f2xm4h95ma8wuggqkvfzgvsnh5z9","salt":null}]}},"events":[
            {"type":"coin_spent","attributes":[{"key":"spender","value":"thor17hwqt302e5f2xm4h95ma8wuggqkvfzgvsnh5z9"},{"key":"amount","value":"2000000rune"}]},
            {"type":"coin_received","attributes":[{"key":"receiver","value":"thor1dheycdevq39qlkxs2a6wuuzyn4aqxhve4qxtxt"},{"key":"amount","value":"2000000rune"}]},
            {"type":"transfer","attributes":[{"key":"recipient","value":"thor1dheycdevq39qlkxs2a6wuuzyn4aqxhve4qxtxt"},{"key":"sender","value":"thor17hwqt302e5f2xm4h95ma8wuggqkvfzgvsnh5z9"},{"key":"amount","value":"2000000rune"}]},
            {"type":"message","attributes":[{"key":"sender","value":"thor17hwqt302e5f2xm4h95ma8wuggqkvfzgvsnh5z9"}]},
            {"type":"tx","attributes":[{"key":"acc_seq","value":"thor17hwqt302e5f2xm4h95ma8wuggqkvfzgvsnh5z9/5826031"}]},
            {"type":"tx","attributes":[{"key":"signature","value":"TFKAJh5wdanf+6p+AOAIbZRKv/o1k2ZgubVSnXTBL7FmNM8/3J9zq1BG/wvtH4G+fJLktuHUhLebxGypJhARNQ=="}]},
            {"type":"message","attributes":[{"key":"action","value":"/types.MsgDeposit"},{"key":"sender","value":"thor17hwqt302e5f2xm4h95ma8wuggqkvfzgvsnh5z9"},{"key":"module","value":"MsgDeposit"},{"key":"msg_index","value":"0"}]},
            {"type":"coin_spent","attributes":[{"key":"spender","value":"thor17hwqt302e5f2xm4h95ma8wuggqkvfzgvsnh5z9"},{"key":"amount","value":"193945327992rune"},{"key":"msg_index","value":"0"}]},
            {"type":"coin_received","attributes":[{"key":"receiver","value":"thor1g98cy3n9mmjrpn0sxmn63lztelera37n8n67c0"},{"key":"amount","value":"193945327992rune"},{"key":"msg_index","value":"0"}]},
            {"type":"transfer","attributes":[{"key":"recipient","value":"thor1g98cy3n9mmjrpn0sxmn63lztelera37n8n67c0"},{"key":"sender","value":"thor17hwqt302e5f2xm4h95ma8wuggqkvfzgvsnh5z9"},{"key":"amount","value":"193945327992rune"},{"key":"msg_index","value":"0"}]},
            {"type":"message","attributes":[{"key":"sender","value":"thor17hwqt302e5f2xm4h95ma8wuggqkvfzgvsnh5z9"},{"key":"msg_index","value":"0"}]}
        ]}}
    """

    // real THORChain MsgDeposit BOND that failed (code 5) and was still charged
    const val THOR_FAILED_DEPOSIT = """
        {"tx_response":{"height":"27937636","txhash":"A9AFEA02641C049E08CC310E626290EC7B22B83EB796AA36F0B202181C321C8E","code":5,"tx":{"body":{"messages":[{"@type":"/types.MsgDeposit","coins":[{"asset":"THOR.RUNE","amount":"641972000000","decimals":"0"}],"memo":"BOND:thor1fj6zv7uvn0t898ch7sxlmjept7lfdnrer8rtpq","signer":"thor1z3e8pxs5fpgcdjpnn92y7xfv90enqm46qtxdl2","salt":null}]}},"events":[
            {"type":"coin_spent","attributes":[{"key":"spender","value":"thor1z3e8pxs5fpgcdjpnn92y7xfv90enqm46qtxdl2"},{"key":"amount","value":"2000000rune"}]},
            {"type":"coin_received","attributes":[{"key":"receiver","value":"thor1dheycdevq39qlkxs2a6wuuzyn4aqxhve4qxtxt"},{"key":"amount","value":"2000000rune"}]},
            {"type":"transfer","attributes":[{"key":"recipient","value":"thor1dheycdevq39qlkxs2a6wuuzyn4aqxhve4qxtxt"},{"key":"sender","value":"thor1z3e8pxs5fpgcdjpnn92y7xfv90enqm46qtxdl2"},{"key":"amount","value":"2000000rune"}]},
            {"type":"message","attributes":[{"key":"sender","value":"thor1z3e8pxs5fpgcdjpnn92y7xfv90enqm46qtxdl2"}]},
            {"type":"tx","attributes":[{"key":"acc_seq","value":"thor1z3e8pxs5fpgcdjpnn92y7xfv90enqm46qtxdl2/17"}]},
            {"type":"tx","attributes":[{"key":"signature","value":"IEWBmw5iXfVDUkwEvURZk5Unhi68Tn1/bO7UwQPKR8sIXGI2JTlkkK1sFvls2WDEghLKM8EA5fHnKPRt9G3XZw=="}]}
        ]}}
    """

    // real THORChain MsgDeposit swap that Midgard reports as a refund
    const val THOR_REFUNDED_DEPOSIT = """
        {"tx_response":{"height":"27961784","txhash":"D18DD519FCBFC4FE6E2D3C4D37ED269387172C3EC276B05731A5D94AB9179D55","code":0,"tx":{"body":{"messages":[{"@type":"/types.MsgDeposit","coins":[{"asset":"THOR.RUNE","amount":"130897827507","decimals":"0"}],"memo":"SWAP:BCH~BCH:thor166n4w5039meulfa3p6ydg60ve6ueac7tlt0jws:235953168/1/1","signer":"thor166n4w5039meulfa3p6ydg60ve6ueac7tlt0jws","salt":null}]}},"events":[
            {"type":"coin_spent","attributes":[{"key":"spender","value":"thor166n4w5039meulfa3p6ydg60ve6ueac7tlt0jws"},{"key":"amount","value":"2000000rune"}]},
            {"type":"coin_received","attributes":[{"key":"receiver","value":"thor1dheycdevq39qlkxs2a6wuuzyn4aqxhve4qxtxt"},{"key":"amount","value":"2000000rune"}]},
            {"type":"transfer","attributes":[{"key":"recipient","value":"thor1dheycdevq39qlkxs2a6wuuzyn4aqxhve4qxtxt"},{"key":"sender","value":"thor166n4w5039meulfa3p6ydg60ve6ueac7tlt0jws"},{"key":"amount","value":"2000000rune"}]},
            {"type":"message","attributes":[{"key":"sender","value":"thor166n4w5039meulfa3p6ydg60ve6ueac7tlt0jws"}]},
            {"type":"tx","attributes":[{"key":"acc_seq","value":"thor166n4w5039meulfa3p6ydg60ve6ueac7tlt0jws/8929690"}]},
            {"type":"tx","attributes":[{"key":"signature","value":"RGvPwXRgC/5/ycwLhNt9IQEeaN3uIuUcAIST3ZcHYzUK/+ctr8M9oITi+4A5+/uIdVhgCdxWH4YcwMh2XC2BhQ=="}]},
            {"type":"message","attributes":[{"key":"action","value":"/types.MsgDeposit"},{"key":"sender","value":"thor166n4w5039meulfa3p6ydg60ve6ueac7tlt0jws"},{"key":"module","value":"MsgDeposit"},{"key":"msg_index","value":"0"}]},
            {"type":"coin_spent","attributes":[{"key":"spender","value":"thor166n4w5039meulfa3p6ydg60ve6ueac7tlt0jws"},{"key":"amount","value":"130897827507rune"},{"key":"msg_index","value":"0"}]},
            {"type":"coin_received","attributes":[{"key":"receiver","value":"thor1g98cy3n9mmjrpn0sxmn63lztelera37n8n67c0"},{"key":"amount","value":"130897827507rune"},{"key":"msg_index","value":"0"}]},
            {"type":"transfer","attributes":[{"key":"recipient","value":"thor1g98cy3n9mmjrpn0sxmn63lztelera37n8n67c0"},{"key":"sender","value":"thor166n4w5039meulfa3p6ydg60ve6ueac7tlt0jws"},{"key":"amount","value":"130897827507rune"},{"key":"msg_index","value":"0"}]},
            {"type":"message","attributes":[{"key":"sender","value":"thor166n4w5039meulfa3p6ydg60ve6ueac7tlt0jws"},{"key":"msg_index","value":"0"}]}
        ]}}
    """

    // real Maya MsgSend of CACAO: base64 event attributes, fee split into three transfers
    const val MAYA_CACAO_SEND = """
        {"tx_response":{"height":"17971283","txhash":"E755EC2203BDD887971ADDB74D1B3986C637FF4B8F9713FAD2D176185E73548E","code":0,"tx":{"body":{"messages":[{"@type":"/types.MsgSend","from_address":"maya1qc30hsy23lgf3hnkwypg3p4ecxw3yad94l2rm7","to_address":"maya1k7arenn38gf3zzj5utdmw8zq732rjcqwfzpjt8","amount":[{"denom":"cacao","amount":"8000000000000"}]}]}},"events":[
            {"type":"tx","attributes":[{"key":"ZmVl","value":null},{"key":"ZmVlX3BheWVy","value":"bWF5YTFxYzMwaHN5MjNsZ2YzaG5rd3lwZzNwNGVjeHczeWFkOTRsMnJtNw=="}]},
            {"type":"tx","attributes":[{"key":"YWNjX3NlcQ==","value":"bWF5YTFxYzMwaHN5MjNsZ2YzaG5rd3lwZzNwNGVjeHczeWFkOTRsMnJtNy81"}]},
            {"type":"tx","attributes":[{"key":"c2lnbmF0dXJl","value":"NmFJMVlSemdwZkhWUTJab3g1d3I0VGk3OEJJNFcvc0VKS2xUZXRLZ0V1OXBkUUdmTEd2TFRGZEJhTk9kYWNWR2k1anJRSnVvUlYvdDhKUVpPTFBZNGc9PQ=="}]},
            {"type":"message","attributes":[{"key":"YWN0aW9u","value":"c2VuZA=="}]},
            {"type":"coin_spent","attributes":[{"key":"c3BlbmRlcg==","value":"bWF5YTFxYzMwaHN5MjNsZ2YzaG5rd3lwZzNwNGVjeHczeWFkOTRsMnJtNw=="},{"key":"YW1vdW50","value":"MTc4MDAwMDAwMGNhY2Fv"}]},
            {"type":"coin_received","attributes":[{"key":"cmVjZWl2ZXI=","value":"bWF5YTFkaGV5Y2RldnEzOXFsa3hzMmE2d3V1enluNGFxeGh2ZTRoYzhzbQ=="},{"key":"YW1vdW50","value":"MTc4MDAwMDAwMGNhY2Fv"}]},
            {"type":"transfer","attributes":[{"key":"cmVjaXBpZW50","value":"bWF5YTFkaGV5Y2RldnEzOXFsa3hzMmE2d3V1enluNGFxeGh2ZTRoYzhzbQ=="},{"key":"c2VuZGVy","value":"bWF5YTFxYzMwaHN5MjNsZ2YzaG5rd3lwZzNwNGVjeHczeWFkOTRsMnJtNw=="},{"key":"YW1vdW50","value":"MTc4MDAwMDAwMGNhY2Fv"}]},
            {"type":"message","attributes":[{"key":"c2VuZGVy","value":"bWF5YTFxYzMwaHN5MjNsZ2YzaG5rd3lwZzNwNGVjeHczeWFkOTRsMnJtNw=="}]},
            {"type":"coin_spent","attributes":[{"key":"c3BlbmRlcg==","value":"bWF5YTFxYzMwaHN5MjNsZ2YzaG5rd3lwZzNwNGVjeHczeWFkOTRsMnJtNw=="},{"key":"YW1vdW50","value":"MjAwMDAwMDAwY2FjYW8="}]},
            {"type":"coin_received","attributes":[{"key":"cmVjZWl2ZXI=","value":"bWF5YTE1NzdzejhqN3hudGhtM2NsM3ZnZnZtZG1rcnA3ZHFyaGQ5dGFmZA=="},{"key":"YW1vdW50","value":"MjAwMDAwMDAwY2FjYW8="}]},
            {"type":"transfer","attributes":[{"key":"cmVjaXBpZW50","value":"bWF5YTE1NzdzejhqN3hudGhtM2NsM3ZnZnZtZG1rcnA3ZHFyaGQ5dGFmZA=="},{"key":"c2VuZGVy","value":"bWF5YTFxYzMwaHN5MjNsZ2YzaG5rd3lwZzNwNGVjeHczeWFkOTRsMnJtNw=="},{"key":"YW1vdW50","value":"MjAwMDAwMDAwY2FjYW8="}]},
            {"type":"message","attributes":[{"key":"c2VuZGVy","value":"bWF5YTFxYzMwaHN5MjNsZ2YzaG5rd3lwZzNwNGVjeHczeWFkOTRsMnJtNw=="}]},
            {"type":"coin_spent","attributes":[{"key":"c3BlbmRlcg==","value":"bWF5YTFxYzMwaHN5MjNsZ2YzaG5rd3lwZzNwNGVjeHczeWFkOTRsMnJtNw=="},{"key":"YW1vdW50","value":"MjAwMDAwMDBjYWNhbw=="}]},
            {"type":"coin_received","attributes":[{"key":"cmVjZWl2ZXI=","value":"bWF5YTE5NzY0eGdhbm1uZGFyemhuN21ybHM3dGMwcjU5YzdnajV0d2t6Nw=="},{"key":"YW1vdW50","value":"MjAwMDAwMDBjYWNhbw=="}]},
            {"type":"transfer","attributes":[{"key":"cmVjaXBpZW50","value":"bWF5YTE5NzY0eGdhbm1uZGFyemhuN21ybHM3dGMwcjU5YzdnajV0d2t6Nw=="},{"key":"c2VuZGVy","value":"bWF5YTFxYzMwaHN5MjNsZ2YzaG5rd3lwZzNwNGVjeHczeWFkOTRsMnJtNw=="},{"key":"YW1vdW50","value":"MjAwMDAwMDBjYWNhbw=="}]},
            {"type":"message","attributes":[{"key":"c2VuZGVy","value":"bWF5YTFxYzMwaHN5MjNsZ2YzaG5rd3lwZzNwNGVjeHczeWFkOTRsMnJtNw=="}]},
            {"type":"coin_spent","attributes":[{"key":"c3BlbmRlcg==","value":"bWF5YTFxYzMwaHN5MjNsZ2YzaG5rd3lwZzNwNGVjeHczeWFkOTRsMnJtNw=="},{"key":"YW1vdW50","value":"ODAwMDAwMDAwMDAwMGNhY2Fv"}]},
            {"type":"coin_received","attributes":[{"key":"cmVjZWl2ZXI=","value":"bWF5YTFrN2FyZW5uMzhnZjN6emo1dXRkbXc4enE3MzJyamNxd2Z6cGp0OA=="},{"key":"YW1vdW50","value":"ODAwMDAwMDAwMDAwMGNhY2Fv"}]},
            {"type":"transfer","attributes":[{"key":"cmVjaXBpZW50","value":"bWF5YTFrN2FyZW5uMzhnZjN6emo1dXRkbXc4enE3MzJyamNxd2Z6cGp0OA=="},{"key":"c2VuZGVy","value":"bWF5YTFxYzMwaHN5MjNsZ2YzaG5rd3lwZzNwNGVjeHczeWFkOTRsMnJtNw=="},{"key":"YW1vdW50","value":"ODAwMDAwMDAwMDAwMGNhY2Fv"}]},
            {"type":"message","attributes":[{"key":"c2VuZGVy","value":"bWF5YTFxYzMwaHN5MjNsZ2YzaG5rd3lwZzNwNGVjeHczeWFkOTRsMnJtNw=="}]},
            {"type":"message","attributes":[{"key":"bW9kdWxl","value":"Z292ZXJuYW5jZQ=="}]}
        ]}}
    """

    // real Maya MsgSend of the MAYA token; its fee is paid in CACAO
    const val MAYA_TOKEN_SEND = """
        {"tx_response":{"height":"17966053","txhash":"017047AEB6C39F91C3EEA5AB53CF4D1061008D506DF0BA1D74A92D416FE9B44D","code":0,"tx":{"body":{"messages":[{"@type":"/types.MsgSend","from_address":"maya1pf7gg2h9kdq7zuj58r7wk8py99awwj9lwvchdx","to_address":"maya1dpc3jzxg0jwp5q4rzk0xp334stuas2t47md5wl","amount":[{"denom":"maya","amount":"640000"}]}]}},"events":[
            {"type":"tx","attributes":[{"key":"ZmVl","value":null},{"key":"ZmVlX3BheWVy","value":"bWF5YTFwZjdnZzJoOWtkcTd6dWo1OHI3d2s4cHk5OWF3d2o5bHd2Y2hkeA=="}]},
            {"type":"tx","attributes":[{"key":"YWNjX3NlcQ==","value":"bWF5YTFwZjdnZzJoOWtkcTd6dWo1OHI3d2s4cHk5OWF3d2o5bHd2Y2hkeC85NjM="}]},
            {"type":"tx","attributes":[{"key":"c2lnbmF0dXJl","value":"WDlNb0hLeHEyWHp3dWNIdkx6VHc4bjNEUk9DNlBjNmdWZGJGckZGQ1RmZ2FybjJ2Mm9Sbi9OdEpOVW0zRWh4c096aFZYNkdRa2FZYzd0alFxRGd3QVE9PQ=="}]},
            {"type":"message","attributes":[{"key":"YWN0aW9u","value":"c2VuZA=="}]},
            {"type":"coin_spent","attributes":[{"key":"c3BlbmRlcg==","value":"bWF5YTFwZjdnZzJoOWtkcTd6dWo1OHI3d2s4cHk5OWF3d2o5bHd2Y2hkeA=="},{"key":"YW1vdW50","value":"MTc4MDAwMDAwMGNhY2Fv"}]},
            {"type":"coin_received","attributes":[{"key":"cmVjZWl2ZXI=","value":"bWF5YTFkaGV5Y2RldnEzOXFsa3hzMmE2d3V1enluNGFxeGh2ZTRoYzhzbQ=="},{"key":"YW1vdW50","value":"MTc4MDAwMDAwMGNhY2Fv"}]},
            {"type":"transfer","attributes":[{"key":"cmVjaXBpZW50","value":"bWF5YTFkaGV5Y2RldnEzOXFsa3hzMmE2d3V1enluNGFxeGh2ZTRoYzhzbQ=="},{"key":"c2VuZGVy","value":"bWF5YTFwZjdnZzJoOWtkcTd6dWo1OHI3d2s4cHk5OWF3d2o5bHd2Y2hkeA=="},{"key":"YW1vdW50","value":"MTc4MDAwMDAwMGNhY2Fv"}]},
            {"type":"message","attributes":[{"key":"c2VuZGVy","value":"bWF5YTFwZjdnZzJoOWtkcTd6dWo1OHI3d2s4cHk5OWF3d2o5bHd2Y2hkeA=="}]},
            {"type":"coin_spent","attributes":[{"key":"c3BlbmRlcg==","value":"bWF5YTFwZjdnZzJoOWtkcTd6dWo1OHI3d2s4cHk5OWF3d2o5bHd2Y2hkeA=="},{"key":"YW1vdW50","value":"MjAwMDAwMDAwY2FjYW8="}]},
            {"type":"coin_received","attributes":[{"key":"cmVjZWl2ZXI=","value":"bWF5YTE1NzdzejhqN3hudGhtM2NsM3ZnZnZtZG1rcnA3ZHFyaGQ5dGFmZA=="},{"key":"YW1vdW50","value":"MjAwMDAwMDAwY2FjYW8="}]},
            {"type":"transfer","attributes":[{"key":"cmVjaXBpZW50","value":"bWF5YTE1NzdzejhqN3hudGhtM2NsM3ZnZnZtZG1rcnA3ZHFyaGQ5dGFmZA=="},{"key":"c2VuZGVy","value":"bWF5YTFwZjdnZzJoOWtkcTd6dWo1OHI3d2s4cHk5OWF3d2o5bHd2Y2hkeA=="},{"key":"YW1vdW50","value":"MjAwMDAwMDAwY2FjYW8="}]},
            {"type":"message","attributes":[{"key":"c2VuZGVy","value":"bWF5YTFwZjdnZzJoOWtkcTd6dWo1OHI3d2s4cHk5OWF3d2o5bHd2Y2hkeA=="}]},
            {"type":"coin_spent","attributes":[{"key":"c3BlbmRlcg==","value":"bWF5YTFwZjdnZzJoOWtkcTd6dWo1OHI3d2s4cHk5OWF3d2o5bHd2Y2hkeA=="},{"key":"YW1vdW50","value":"MjAwMDAwMDBjYWNhbw=="}]},
            {"type":"coin_received","attributes":[{"key":"cmVjZWl2ZXI=","value":"bWF5YTE5NzY0eGdhbm1uZGFyemhuN21ybHM3dGMwcjU5YzdnajV0d2t6Nw=="},{"key":"YW1vdW50","value":"MjAwMDAwMDBjYWNhbw=="}]},
            {"type":"transfer","attributes":[{"key":"cmVjaXBpZW50","value":"bWF5YTE5NzY0eGdhbm1uZGFyemhuN21ybHM3dGMwcjU5YzdnajV0d2t6Nw=="},{"key":"c2VuZGVy","value":"bWF5YTFwZjdnZzJoOWtkcTd6dWo1OHI3d2s4cHk5OWF3d2o5bHd2Y2hkeA=="},{"key":"YW1vdW50","value":"MjAwMDAwMDBjYWNhbw=="}]},
            {"type":"message","attributes":[{"key":"c2VuZGVy","value":"bWF5YTFwZjdnZzJoOWtkcTd6dWo1OHI3d2s4cHk5OWF3d2o5bHd2Y2hkeA=="}]},
            {"type":"coin_spent","attributes":[{"key":"c3BlbmRlcg==","value":"bWF5YTFwZjdnZzJoOWtkcTd6dWo1OHI3d2s4cHk5OWF3d2o5bHd2Y2hkeA=="},{"key":"YW1vdW50","value":"NjQwMDAwbWF5YQ=="}]},
            {"type":"coin_received","attributes":[{"key":"cmVjZWl2ZXI=","value":"bWF5YTFkcGMzanp4ZzBqd3A1cTRyemsweHAzMzRzdHVhczJ0NDdtZDV3bA=="},{"key":"YW1vdW50","value":"NjQwMDAwbWF5YQ=="}]},
            {"type":"transfer","attributes":[{"key":"cmVjaXBpZW50","value":"bWF5YTFkcGMzanp4ZzBqd3A1cTRyemsweHAzMzRzdHVhczJ0NDdtZDV3bA=="},{"key":"c2VuZGVy","value":"bWF5YTFwZjdnZzJoOWtkcTd6dWo1OHI3d2s4cHk5OWF3d2o5bHd2Y2hkeA=="},{"key":"YW1vdW50","value":"NjQwMDAwbWF5YQ=="}]},
            {"type":"message","attributes":[{"key":"c2VuZGVy","value":"bWF5YTFwZjdnZzJoOWtkcTd6dWo1OHI3d2s4cHk5OWF3d2o5bHd2Y2hkeA=="}]},
            {"type":"message","attributes":[{"key":"bW9kdWxl","value":"Z292ZXJuYW5jZQ=="}]}
        ]}}
    """

    // real Maya MsgSend rejected while the chain was halted; nothing was charged
    const val MAYA_UNCHARGED_SEND = """
        {"tx_response":{"height":"18467426","txhash":"A01E98CD3ADFCE0815BCD68B9F8A487D527F3DA2FCF5FE23BDA69A64E62938C9","code":1,"tx":{"body":{"messages":[{"@type":"/types.MsgSend","from_address":"maya1569spcpfqpxpxuqj5mffkyarngx8dgu0uvsudx","to_address":"maya1szcwpnfrn2r9jqquv3v58qmrjqd0hzek485qee","amount":[{"denom":"cacao","amount":"300000000000"}]}]}},"events":[
            {"type":"tx","attributes":[{"key":"ZmVl","value":null},{"key":"ZmVlX3BheWVy","value":"bWF5YTE1NjlzcGNwZnFweHB4dXFqNW1mZmt5YXJuZ3g4ZGd1MHV2c3VkeA=="}]},
            {"type":"tx","attributes":[{"key":"YWNjX3NlcQ==","value":"bWF5YTE1NjlzcGNwZnFweHB4dXFqNW1mZmt5YXJuZ3g4ZGd1MHV2c3VkeC8w"}]},
            {"type":"tx","attributes":[{"key":"c2lnbmF0dXJl","value":"dmo1QWgyeWZLdDJBMUZDbXBLUXVOM3REQXRwWVhHWDk0VTFJV2daRHdYYzQrV0hLUlFvS2tic3IvQzZ6WFo1ZVFOZjZmT1V0VDRScHkvSTJsQWd1ZHc9PQ=="}]}
        ]}}
    """

    // real Maya tx with two MsgDeposit messages: the fee is charged once per message
    const val MAYA_TWO_DEPOSITS = """
        {"tx_response":{"height":"17978008","txhash":"02149B6838801EA7920E968EE20311C7DF3C5C5A2C03818B29AC0CFF2F9E54D7","code":0,"tx":{"body":{"messages":[{"@type":"/types.MsgDeposit","coins":[{"asset":"MAYA.CACAO","amount":"40000000000000000","decimals":"0"}],"memo":"=:BTC.BTC:bc1q0hsgwunccczelq05ucpmfz268eyy5jr2y5l646","signer":"maya1dl3yrfpedyr5jfr0r86s2apjltnjqgszmwsv8x"},{"@type":"/types.MsgDeposit","coins":[{"asset":"ARB~ETH","amount":"30000","decimals":"0"}],"memo":"trade-:0xa2f246f82995CBcCA8eD0d9F251383881A5E423e","signer":"maya1dl3yrfpedyr5jfr0r86s2apjltnjqgszmwsv8x"}]}},"events":[
            {"type":"tx","attributes":[{"key":"ZmVl","value":null},{"key":"ZmVlX3BheWVy","value":"bWF5YTFkbDN5cmZwZWR5cjVqZnIwcjg2czJhcGpsdG5qcWdzem13c3Y4eA=="}]},
            {"type":"tx","attributes":[{"key":"YWNjX3NlcQ==","value":"bWF5YTFkbDN5cmZwZWR5cjVqZnIwcjg2czJhcGpsdG5qcWdzem13c3Y4eC8xNA=="}]},
            {"type":"tx","attributes":[{"key":"c2lnbmF0dXJl","value":"UU13empMMXNmV1RwZUF3RFJOczFGWWtYbFlvTmtueW4vT0NFdCtieFdHaGgwanFFMTR6N2VZNGZXOGdUM1d1a2lJOFdFZ3FnRUpheTl1VXpLRzJ3enc9PQ=="}]},
            {"type":"message","attributes":[{"key":"YWN0aW9u","value":"ZGVwb3NpdA=="}]},
            {"type":"coin_spent","attributes":[{"key":"c3BlbmRlcg==","value":"bWF5YTFkbDN5cmZwZWR5cjVqZnIwcjg2czJhcGpsdG5qcWdzem13c3Y4eA=="},{"key":"YW1vdW50","value":"MTc4MDAwMDAwMGNhY2Fv"}]},
            {"type":"coin_received","attributes":[{"key":"cmVjZWl2ZXI=","value":"bWF5YTFkaGV5Y2RldnEzOXFsa3hzMmE2d3V1enluNGFxeGh2ZTRoYzhzbQ=="},{"key":"YW1vdW50","value":"MTc4MDAwMDAwMGNhY2Fv"}]},
            {"type":"transfer","attributes":[{"key":"cmVjaXBpZW50","value":"bWF5YTFkaGV5Y2RldnEzOXFsa3hzMmE2d3V1enluNGFxeGh2ZTRoYzhzbQ=="},{"key":"c2VuZGVy","value":"bWF5YTFkbDN5cmZwZWR5cjVqZnIwcjg2czJhcGpsdG5qcWdzem13c3Y4eA=="},{"key":"YW1vdW50","value":"MTc4MDAwMDAwMGNhY2Fv"}]},
            {"type":"message","attributes":[{"key":"c2VuZGVy","value":"bWF5YTFkbDN5cmZwZWR5cjVqZnIwcjg2czJhcGpsdG5qcWdzem13c3Y4eA=="}]},
            {"type":"coin_spent","attributes":[{"key":"c3BlbmRlcg==","value":"bWF5YTFkbDN5cmZwZWR5cjVqZnIwcjg2czJhcGpsdG5qcWdzem13c3Y4eA=="},{"key":"YW1vdW50","value":"MjAwMDAwMDAwY2FjYW8="}]},
            {"type":"coin_received","attributes":[{"key":"cmVjZWl2ZXI=","value":"bWF5YTE1NzdzejhqN3hudGhtM2NsM3ZnZnZtZG1rcnA3ZHFyaGQ5dGFmZA=="},{"key":"YW1vdW50","value":"MjAwMDAwMDAwY2FjYW8="}]},
            {"type":"transfer","attributes":[{"key":"cmVjaXBpZW50","value":"bWF5YTE1NzdzejhqN3hudGhtM2NsM3ZnZnZtZG1rcnA3ZHFyaGQ5dGFmZA=="},{"key":"c2VuZGVy","value":"bWF5YTFkbDN5cmZwZWR5cjVqZnIwcjg2czJhcGpsdG5qcWdzem13c3Y4eA=="},{"key":"YW1vdW50","value":"MjAwMDAwMDAwY2FjYW8="}]},
            {"type":"message","attributes":[{"key":"c2VuZGVy","value":"bWF5YTFkbDN5cmZwZWR5cjVqZnIwcjg2czJhcGpsdG5qcWdzem13c3Y4eA=="}]},
            {"type":"coin_spent","attributes":[{"key":"c3BlbmRlcg==","value":"bWF5YTFkbDN5cmZwZWR5cjVqZnIwcjg2czJhcGpsdG5qcWdzem13c3Y4eA=="},{"key":"YW1vdW50","value":"MjAwMDAwMDBjYWNhbw=="}]},
            {"type":"coin_received","attributes":[{"key":"cmVjZWl2ZXI=","value":"bWF5YTE5NzY0eGdhbm1uZGFyemhuN21ybHM3dGMwcjU5YzdnajV0d2t6Nw=="},{"key":"YW1vdW50","value":"MjAwMDAwMDBjYWNhbw=="}]},
            {"type":"transfer","attributes":[{"key":"cmVjaXBpZW50","value":"bWF5YTE5NzY0eGdhbm1uZGFyemhuN21ybHM3dGMwcjU5YzdnajV0d2t6Nw=="},{"key":"c2VuZGVy","value":"bWF5YTFkbDN5cmZwZWR5cjVqZnIwcjg2czJhcGpsdG5qcWdzem13c3Y4eA=="},{"key":"YW1vdW50","value":"MjAwMDAwMDBjYWNhbw=="}]},
            {"type":"message","attributes":[{"key":"c2VuZGVy","value":"bWF5YTFkbDN5cmZwZWR5cjVqZnIwcjg2czJhcGpsdG5qcWdzem13c3Y4eA=="}]},
            {"type":"coin_spent","attributes":[{"key":"c3BlbmRlcg==","value":"bWF5YTFkbDN5cmZwZWR5cjVqZnIwcjg2czJhcGpsdG5qcWdzem13c3Y4eA=="},{"key":"YW1vdW50","value":"NDAwMDAwMDAwMDAwMDAwMDBjYWNhbw=="}]},
            {"type":"coin_received","attributes":[{"key":"cmVjZWl2ZXI=","value":"bWF5YTFnOThjeTNuOW1tanJwbjBzeG1uNjNsenRlbGVyYTM3bjh5eWp3bA=="},{"key":"YW1vdW50","value":"NDAwMDAwMDAwMDAwMDAwMDBjYWNhbw=="}]},
            {"type":"transfer","attributes":[{"key":"cmVjaXBpZW50","value":"bWF5YTFnOThjeTNuOW1tanJwbjBzeG1uNjNsenRlbGVyYTM3bjh5eWp3bA=="},{"key":"c2VuZGVy","value":"bWF5YTFkbDN5cmZwZWR5cjVqZnIwcjg2czJhcGpsdG5qcWdzem13c3Y4eA=="},{"key":"YW1vdW50","value":"NDAwMDAwMDAwMDAwMDAwMDBjYWNhbw=="}]},
            {"type":"message","attributes":[{"key":"c2VuZGVy","value":"bWF5YTFkbDN5cmZwZWR5cjVqZnIwcjg2czJhcGpsdG5qcWdzem13c3Y4eA=="}]},
            {"type":"message","attributes":[{"key":"YWN0aW9u","value":"ZGVwb3NpdA=="}]},
            {"type":"coin_spent","attributes":[{"key":"c3BlbmRlcg==","value":"bWF5YTFkbDN5cmZwZWR5cjVqZnIwcjg2czJhcGpsdG5qcWdzem13c3Y4eA=="},{"key":"YW1vdW50","value":"MTc4MDAwMDAwMGNhY2Fv"}]},
            {"type":"coin_received","attributes":[{"key":"cmVjZWl2ZXI=","value":"bWF5YTFkaGV5Y2RldnEzOXFsa3hzMmE2d3V1enluNGFxeGh2ZTRoYzhzbQ=="},{"key":"YW1vdW50","value":"MTc4MDAwMDAwMGNhY2Fv"}]},
            {"type":"transfer","attributes":[{"key":"cmVjaXBpZW50","value":"bWF5YTFkaGV5Y2RldnEzOXFsa3hzMmE2d3V1enluNGFxeGh2ZTRoYzhzbQ=="},{"key":"c2VuZGVy","value":"bWF5YTFkbDN5cmZwZWR5cjVqZnIwcjg2czJhcGpsdG5qcWdzem13c3Y4eA=="},{"key":"YW1vdW50","value":"MTc4MDAwMDAwMGNhY2Fv"}]},
            {"type":"message","attributes":[{"key":"c2VuZGVy","value":"bWF5YTFkbDN5cmZwZWR5cjVqZnIwcjg2czJhcGpsdG5qcWdzem13c3Y4eA=="}]},
            {"type":"coin_spent","attributes":[{"key":"c3BlbmRlcg==","value":"bWF5YTFkbDN5cmZwZWR5cjVqZnIwcjg2czJhcGpsdG5qcWdzem13c3Y4eA=="},{"key":"YW1vdW50","value":"MjAwMDAwMDAwY2FjYW8="}]},
            {"type":"coin_received","attributes":[{"key":"cmVjZWl2ZXI=","value":"bWF5YTE1NzdzejhqN3hudGhtM2NsM3ZnZnZtZG1rcnA3ZHFyaGQ5dGFmZA=="},{"key":"YW1vdW50","value":"MjAwMDAwMDAwY2FjYW8="}]},
            {"type":"transfer","attributes":[{"key":"cmVjaXBpZW50","value":"bWF5YTE1NzdzejhqN3hudGhtM2NsM3ZnZnZtZG1rcnA3ZHFyaGQ5dGFmZA=="},{"key":"c2VuZGVy","value":"bWF5YTFkbDN5cmZwZWR5cjVqZnIwcjg2czJhcGpsdG5qcWdzem13c3Y4eA=="},{"key":"YW1vdW50","value":"MjAwMDAwMDAwY2FjYW8="}]},
            {"type":"message","attributes":[{"key":"c2VuZGVy","value":"bWF5YTFkbDN5cmZwZWR5cjVqZnIwcjg2czJhcGpsdG5qcWdzem13c3Y4eA=="}]},
            {"type":"coin_spent","attributes":[{"key":"c3BlbmRlcg==","value":"bWF5YTFkbDN5cmZwZWR5cjVqZnIwcjg2czJhcGpsdG5qcWdzem13c3Y4eA=="},{"key":"YW1vdW50","value":"MjAwMDAwMDBjYWNhbw=="}]},
            {"type":"coin_received","attributes":[{"key":"cmVjZWl2ZXI=","value":"bWF5YTE5NzY0eGdhbm1uZGFyemhuN21ybHM3dGMwcjU5YzdnajV0d2t6Nw=="},{"key":"YW1vdW50","value":"MjAwMDAwMDBjYWNhbw=="}]},
            {"type":"transfer","attributes":[{"key":"cmVjaXBpZW50","value":"bWF5YTE5NzY0eGdhbm1uZGFyemhuN21ybHM3dGMwcjU5YzdnajV0d2t6Nw=="},{"key":"c2VuZGVy","value":"bWF5YTFkbDN5cmZwZWR5cjVqZnIwcjg2czJhcGpsdG5qcWdzem13c3Y4eA=="},{"key":"YW1vdW50","value":"MjAwMDAwMDBjYWNhbw=="}]},
            {"type":"message","attributes":[{"key":"c2VuZGVy","value":"bWF5YTFkbDN5cmZwZWR5cjVqZnIwcjg2czJhcGpsdG5qcWdzem13c3Y4eA=="}]},
            {"type":"trade_account_withdraw","attributes":[{"key":"YW1vdW50","value":"MzAwMDA="},{"key":"YXNzZXQ=","value":"QVJCfkVUSA=="},{"key":"Y2FjYW9fYWRkcmVzcw==","value":"bWF5YTFkbDN5cmZwZWR5cjVqZnIwcjg2czJhcGpsdG5qcWdzem13c3Y4eA=="},{"key":"YXNzZXRfYWRkcmVzcw==","value":"MHhhMmYyNDZmODI5OTVDQmNDQThlRDBkOUYyNTEzODM4ODFBNUU0MjNl"},{"key":"dHhfaWQ=","value":"MDIxNDlCNjgzODgwMUVBNzkyMEU5NjhFRTIwMzExQzdERjNDNUM1QTJDMDM4MThCMjlBQzBDRkYyRjlFNTRENw=="}]},
            {"type":"fee","attributes":[{"key":"dHhfaWQ=","value":"MDIxNDlCNjgzODgwMUVBNzkyMEU5NjhFRTIwMzExQzdERjNDNUM1QTJDMDM4MThCMjlBQzBDRkYyRjlFNTRENw=="},{"key":"Y29pbnM=","value":"MTM0NDUgQVJCLkVUSA=="},{"key":"cG9vbF9kZWR1Y3Q=","value":"MjM1ODc1NjMwNzY="}]},
            {"type":"coin_spent","attributes":[{"key":"c3BlbmRlcg==","value":"bWF5YTFnOThjeTNuOW1tanJwbjBzeG1uNjNsenRlbGVyYTM3bjh5eWp3bA=="},{"key":"YW1vdW50","value":"MjM1ODc1NjMwNzZjYWNhbw=="}]},
            {"type":"coin_received","attributes":[{"key":"cmVjZWl2ZXI=","value":"bWF5YTFkaGV5Y2RldnEzOXFsa3hzMmE2d3V1enluNGFxeGh2ZTRoYzhzbQ=="},{"key":"YW1vdW50","value":"MjM1ODc1NjMwNzZjYWNhbw=="}]},
            {"type":"transfer","attributes":[{"key":"cmVjaXBpZW50","value":"bWF5YTFkaGV5Y2RldnEzOXFsa3hzMmE2d3V1enluNGFxeGh2ZTRoYzhzbQ=="},{"key":"c2VuZGVy","value":"bWF5YTFnOThjeTNuOW1tanJwbjBzeG1uNjNsenRlbGVyYTM3bjh5eWp3bA=="},{"key":"YW1vdW50","value":"MjM1ODc1NjMwNzZjYWNhbw=="}]},
            {"type":"message","attributes":[{"key":"c2VuZGVy","value":"bWF5YTFnOThjeTNuOW1tanJwbjBzeG1uNjNsenRlbGVyYTM3bjh5eWp3bA=="}]},
            {"type":"scheduled_outbound","attributes":[{"key":"Y2hhaW4=","value":"QVJC"},{"key":"dG9fYWRkcmVzcw==","value":"MHhhMmYyNDZmODI5OTVDQmNDQThlRDBkOUYyNTEzODM4ODFBNUU0MjNl"},{"key":"dmF1bHRfcHViX2tleQ==","value":"bWF5YXB1YjFhZGR3bnBlcHFkZ2FmZ2Q2Z3Y4eDA5bTl2dTZmdnF6a2owZTB2MndqNjRlenM3dm01cDJ3NXRtZjB0MGh2cGxqbWNo"},{"key":"Y29pbl9hc3NldA==","value":"QVJCLkVUSA=="},{"key":"Y29pbl9hbW91bnQ=","value":"MTY1NTU="},{"key":"Y29pbl9kZWNpbWFscw==","value":"MA=="},{"key":"bWVtbw==","value":"T1VUOjAyMTQ5QjY4Mzg4MDFFQTc5MjBFOTY4RUUyMDMxMUM3REYzQzVDNUEyQzAzODE4QjI5QUMwQ0ZGMkY5RTU0RDc="},{"key":"Z2FzX3JhdGU=","value":"NDU="},{"key":"aW5faGFzaA==","value":"MDIxNDlCNjgzODgwMUVBNzkyMEU5NjhFRTIwMzExQzdERjNDNUM1QTJDMDM4MThCMjlBQzBDRkYyRjlFNTRENw=="},{"key":"b3V0X2hhc2g=","value":null},{"key":"bW9kdWxlX25hbWU=","value":null},{"key":"bWF4X2dhc19hc3NldF8w","value":"QVJCLkVUSA=="},{"key":"bWF4X2dhc19hbW91bnRfMA==","value":"MjI1MDA="},{"key":"bWF4X2dhc19kZWNpbWFsc18w","value":"OA=="}]}
        ]}}
    """

    // real Midgard swap action of THOR_SWAP_DEPOSIT
    const val THOR_SWAP_ACTION = """
        {"date":"1790222790923895464","height":"27961524","in":[{"address":"thor17hwqt302e5f2xm4h95ma8wuggqkvfzgvsnh5z9","coins":[{"amount":"193945327992","asset":"THOR.RUNE"}],"txID":"59444566178C0DFDE426F95E2E5AFDF9BB05D5CF5662BA1D70ED1D2428ECC3D1"}],"metadata":{"swap":{"affiliateAddress":"","affiliateFee":"0","inPriceUSD":"0.6210709651191182","isStreamingSwap":false,"liquidityFee":"194102625","memo":"=:BCH~BCH:thor17hwqt302e5f2xm4h95ma8wuggqkvfzgvsnh5z9:352271308/1/1","networkFees":[],"outPriceUSD":"341.8593772135242","swapSlip":"10","swapTarget":"352271308","txType":"swap"}},"out":[{"address":"thor17hwqt302e5f2xm4h95ma8wuggqkvfzgvsnh5z9","coins":[{"amount":"352337310","asset":"BCH~BCH"}],"height":"27961524","txID":""},{"address":"thor17hwqt302e5f2xm4h95ma8wuggqkvfzgvsnh5z9","coins":[{"amount":"352337310","asset":"BCH~BCH"}],"txID":"59444566178C0DFDE426F95E2E5AFDF9BB05D5CF5662BA1D70ED1D2428ECC3D1"}],"pools":["BCH.BCH"],"status":"success","type":"swap"}
    """

    // real Midgard swap action whose networkFees is the outbound fee; THORNode answers 404 for its hash
    const val THOR_SWAP_WITH_OUTBOUND_FEE_ACTION = """
        {"date":"1790223729851503449","height":"27961665","in":[{"address":"thor1dl7un46w7l7f3ewrnrm6nq58nerjtp0dradjtd","coins":[{"amount":"19840347300","asset":"THOR.RUNE"}],"txID":"5BF80D0BCF354B0DAF35FDA033F7984F5D3CBACD7961ED7235C9D0D8B7FC6BFB"}],"metadata":{"swap":{"affiliateAddress":"","affiliateFee":"0","inPriceUSD":"0.6220412595062988","isStreamingSwap":false,"liquidityFee":"19841417","memo":"THOR-PREFERRED-ASSET-v7","networkFees":[{"amount":"24988000","asset":"ETH.USDC-0XA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48"}],"outPriceUSD":"1.006310097047339","swapSlip":"10","swapTarget":"0","txType":"unknown"}},"out":[{"address":"0x6e9426c27eec5b384ad2af85875e14b0eb74b233","coins":[{"amount":"12206021200","asset":"ETH.USDC-0XA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48"}],"height":"27961669","txID":"B6828D358590D1521335CC5E5A28D397E45BB4F065ABB44B74171A3311927983"}],"pools":["ETH.USDC-0XA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48"],"status":"success","type":"swap"}
    """

    // real Midgard refund action of THOR_REFUNDED_DEPOSIT
    const val THOR_REFUND_ACTION = """
        {"date":"1790224536101703233","height":"27961784","in":[{"address":"thor166n4w5039meulfa3p6ydg60ve6ueac7tlt0jws","coins":[{"amount":"130897827507","asset":"THOR.RUNE"}],"txID":"D18DD519FCBFC4FE6E2D3C4D37ED269387172C3EC276B05731A5D94AB9179D55"}],"metadata":{"refund":{"affiliateAddress":"","affiliateFee":"0","memo":"SWAP:BCH~BCH:thor166n4w5039meulfa3p6ydg60ve6ueac7tlt0jws:235953168/1/1","networkFees":[],"reason":"emit asset 235899655 less than price limit 235953168","txType":"swap"}},"out":[{"address":"thor166n4w5039meulfa3p6ydg60ve6ueac7tlt0jws","coins":[{"amount":"130897827507","asset":"THOR.RUNE"}],"height":"27961784","txID":""}],"pools":[],"status":"success","type":"refund"}
    """
}
