<#if mode.name() == "UPDATE">
UPDATE ${myuniversity}_${mymodule}.transaction tr
SET jsonb = jsonb_set(jsonb, '{encumbrance,orderStatus}',
                      (SELECT jsonb -> 'workflowStatus'
                       from ${myuniversity}_mod_orders_storage.purchase_order
                       where jsonb ->> 'id' = tr.jsonb #>> '{encumbrance,sourcePurchaseOrderId}'))
WHERE jsonb ? 'encumbrance';
</#if>
