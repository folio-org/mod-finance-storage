CREATE OR REPLACE VIEW ${myuniversity}_${mymodule}.transaction_totals_view AS
with allocations_without_initial_entry as(
	select c2.id
	from(
		select row_number() over(partition by c1.fiscalyearid, c1.tofundid order by c1.creation_date) rn, *
		  from ${myuniversity}_${mymodule}.transaction c1
		  where (c1.jsonb->>'transactionType') = 'Allocation'
		    and c1.fromfundid is null
	) c2
	where c2.rn > 1
)

select t4.fiscal_year_id id,
			 jsonb_build_object(
		     'fiscalYearId', t4.fiscal_year_id,
	       'transactionType', t4.transaction_type,
		     'currency', t4.currency,
		     'fromFundId', t4.from_fund_id,
		     'toFundId', t4.to_fund_id,
		     'amount', t4.amount
		   ) jsonb
from(
	select (t3.jsonb->>'fiscalYearId') fiscal_year_id,
	       (t3.jsonb->>'transactionType') transaction_type,
	       (t3.jsonb->>'currency') currency,
	       (t3.jsonb->>'fromFundId') from_fund_id,
	       (t3.jsonb->>'toFundId') to_fund_id,
	       coalesce(sum((t3.jsonb->>'amount')::numeric), 0) amount
	from(
	  select t1.*
	    from ${myuniversity}_${mymodule}.transaction t1
	      left join allocations_without_initial_entry c3 on c3.id = t1.id
	    where (t1.jsonb->>'transactionType') = 'Allocation'
	      and ((t1.fromfundid is null and c3.id = t1.id) or (t1.fromfundid is not null))
	   union all
	   select t2.*
	     from ${myuniversity}_${mymodule}.transaction t2
	     where (t2.jsonb->>'transactionType') in('Transfer', 'Rollover transfer')
	) t3
	group by (t3.jsonb->>'fiscalYearId'),
	         (t3.jsonb->>'transactionType'),
	         (t3.jsonb->>'currency'),
	         (t3.jsonb->>'fromFundId'),
	         (t3.jsonb->>'toFundId')
) t4
;
