#!/usr/env/Rscript

library(dplyr)

quant_dir <- "quantification/"

# Combine Cell Quantification Results

# Tumor area cell type counts
tumor_cell_type_files <- Sys.glob(paste0(quant_dir, "*_tumor_area_cell_type_counts.csv"))
tumor_cell_type_df <- data.frame()
for (f in tumor_cell_type_files) {
  df <- read.csv(f, check.names=FALSE)
  tumor_cell_type_df <- bind_rows(tumor_cell_type_df, df)
}
write.csv(tumor_cell_type_df, paste0(quant_dir, "tumor_area_cell_type_counts.csv"), row.names=FALSE, quote=FALSE)

# Tumor gland cell type counts
gland_cell_type_files <- Sys.glob(paste0(quant_dir, "*_tumor_gland_cell_type_counts.csv"))
gland_cell_type_df <- data.frame()
for (f in gland_cell_type_files) {
  df <- read.csv(f, check.names=FALSE)
  gland_cell_type_df <- bind_rows(gland_cell_type_df, df)
}
write.csv(gland_cell_type_df, paste0(quant_dir, "tumor_gland_cell_type_counts.csv"), row.names=FALSE, quote=FALSE)

cell_subtype_files <- Sys.glob(paste0(quant_dir, "*_tumor_area_cell_subtype_counts.csv"))
cell_subtype_df <- data.frame()
for (f in cell_subtype_files) {
  df <- read.csv(f, check.names=FALSE)
  cell_subtype_df <- bind_rows(cell_subtype_df, df)
}

cell_subtype_df[is.na(cell_subtype_df)] <- 0

cell_subtype_df$`Total Classified` <- rowSums(cell_subtype_df %>% select(!c("Sample", "Unknown")))
cell_subtype_df$`Total Immune` <- rowSums(cell_subtype_df %>% select(!c("Sample", "Unknown", "Total Classified") & 
                                                                       !starts_with("Epithelial")))

write.csv(cell_subtype_df, paste0(quant_dir, "tumor_area_cell_subtype_counts.csv"), row.names=FALSE, quote=FALSE)

# Calc PD-L1 Scores

# CPS = # PD-L1-positive cells / total # tumor cells * 100
# TPS (%) = # PD-L1-positive tumor cells / total # tumor cells * 100

tumor_cell_type_df <- tumor_cell_type_df %>% mutate(PDL1_CPS=100*`Total Classified PD-L1`/Epithelial, 
                                                    PDL1_TPS=100*`Total Epithelial PD-L1`/Epithelial,
                                                    PDL2_CPS=100*`Total Classified PD-L2`/Epithelial, 
                                                    PDL2_TPS=100*`Total Epithelial PD-L2`/Epithelial)

pdl1_df <- tumor_cell_type_df %>% select(Sample, PDL1_CPS, PDL1_TPS) %>% rename(CPS=PDL1_CPS, TPS=PDL1_TPS)
write.csv(pdl1_df, paste0(quant_dir, "tumor_area_PDL1.csv"), row.names=FALSE, quote=FALSE)

pdl2_df <- tumor_cell_type_df %>% select(Sample, PDL2_CPS, PDL2_TPS) %>% rename(CPS=PDL2_CPS, TPS=PDL2_TPS)
write.csv(pdl2_df, paste0(quant_dir, "tumor_area_PDL2.csv"), row.names=FALSE, quote=FALSE)
