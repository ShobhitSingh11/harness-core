package handler

import (
	"encoding/json"
	"net/http"
	"time"

	"github.com/wings-software/portal/product/ci/ti-service/config"
	"github.com/wings-software/portal/product/ci/ti-service/db"
	"github.com/wings-software/portal/product/ci/ti-service/tidb"
	"github.com/wings-software/portal/product/ci/ti-service/types"
	"go.uber.org/zap"
)

// HandleSelect returns an http.HandlerFunc that figures out which tests to run
// based on the files provided.
func HandleSelect(tidb tidb.TiDB, db db.Db, config config.Config, log *zap.SugaredLogger) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		st := time.Now()
		ctx := r.Context()

		// TODO: Use this information while retrieving from TIDB
		err := validate(r, accountIDParam, orgIdParam, projectIdParam, pipelineIdParam, buildIdParam,
			stageIdParam, stepIdParam, repoParam, shaParam, branchParam)
		if err != nil {
			WriteInternalError(w, err)
			return
		}
		accountId := r.FormValue(accountIDParam)
		orgId := r.FormValue(orgIdParam)
		projectId := r.FormValue(projectIdParam)
		pipelineId := r.FormValue(pipelineIdParam)
		buildId := r.FormValue(buildIdParam)
		stageId := r.FormValue(stageIdParam)
		stepId := r.FormValue(stepIdParam)
		repo := r.FormValue(repoParam)
		branch := r.FormValue(branchParam)
		sha := r.FormValue(shaParam)

		var req types.SelectTestsReq
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			WriteBadRequest(w, err)
			log.Errorw("api: could not unmarshal input for test selection",
				"account_id", accountId, "repo", repo, "branch", branch, "sha", sha, zap.Error(err))
			return
		}
		log.Infow("got a files list", "account_id", accountId, "files", req.Files, "repo", repo, "branch", branch, "sha", sha)

		// Make call to Mongo DB to get the tests to run
		selected, err := tidb.GetTestsToRun(ctx, req)
		if err != nil {
			WriteInternalError(w, err)
			log.Errorw("api: could not select tests", "account_id", accountId,
				"repo", repo, "branch", branch, "sha", sha, zap.Error(err))
			return
		}

		// Write changed file information to timescaleDB
		err = db.WriteDiffFiles(ctx, config.TimeScaleDb.CoverageTable, accountId, orgId,
			projectId, pipelineId, buildId, stageId, stepId, req.Files)
		if err != nil {
			WriteInternalError(w, err)
			log.Errorw("api: could not write changed file information", "account_id", accountId,
				"repo", repo, "branch", branch, "sha", sha, zap.Error(err))
		}

		// Classify and write the test selection stats to timescaleDB
		err = db.WriteSelectedTests(ctx, config.TimeScaleDb.SelectionTable, accountId, orgId,
			projectId, pipelineId, buildId, stageId, stepId, selected)
		if err != nil {
			WriteInternalError(w, err)
			log.Errorw("api: could not write selected tests", "account_id", accountId,
				"repo", repo, "branch", branch, "sha", sha, zap.Error(err))
			return
		}

		// Write the selected tests back
		WriteJSON(w, selected, 200)
		log.Infow("completed test selection", "account_id", accountId,
			"repo", repo, "branch", branch, "sha", sha, "tests", selected.Tests,
			"num_tests", len(selected.Tests), "time_taken", time.Since(st))

	}
}

func HandleOverview(db db.Db, config config.Config, log *zap.SugaredLogger) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		st := time.Now()
		ctx := r.Context()

		// TODO: Use this information while retrieving from TIDB
		err := validate(r, accountIDParam, orgIdParam, projectIdParam, pipelineIdParam, buildIdParam)
		if err != nil {
			WriteInternalError(w, err)
			return
		}
		accountId := r.FormValue(accountIDParam)
		orgId := r.FormValue(orgIdParam)
		projectId := r.FormValue(projectIdParam)
		pipelineId := r.FormValue(pipelineIdParam)
		buildId := r.FormValue(buildIdParam)

		overview, err := db.GetSelectionOverview(ctx, config.TimeScaleDb.SelectionTable, accountId,
			orgId, projectId, pipelineId, buildId)
		if err != nil {
			log.Errorw("could not get TI overview from DB", zap.Error(err))
			WriteInternalError(w, err)
			return
		}

		// Write the selected tests back
		WriteJSON(w, overview, 200)
		log.Infow("retrieved test overview", "account_id", accountId, "time_taken", time.Since(st))

	}
}
